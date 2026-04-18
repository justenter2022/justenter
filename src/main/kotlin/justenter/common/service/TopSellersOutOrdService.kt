package justenter.common.service

import justenter.cjdeliveryapi.service.CjAddressService
import justenter.cjdeliveryapi.service.CjBookingService
import justenter.cjdeliveryapi.service.CjInvoiceService
import justenter.common.dto.BarcodeScanErrorType
import justenter.common.dto.BarcodeScanResult
import justenter.common.entity.Brand
import justenter.common.entity.TopSellersOutOrd
import justenter.common.repository.BrandRepository
import justenter.common.repository.TopSellersOutOrdRepository
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class TopSellersOutOrdService(
    private val topSellersOutOrdRepository: TopSellersOutOrdRepository,
    private val brandRepository: BrandRepository,
    private val cjAddressService: CjAddressService,
    private val cjInvoiceService: CjInvoiceService,
    private val cjBookingService: CjBookingService,
    private val shippingLabelService: ShippingLabelService,
    @Value("\${app.invoice.base-path:./invoice}") private val invoiceBasePath: String,
    @Value("\${app.sender.name:}") private val senderName: String,
    @Value("\${app.sender.tel:}") private val senderTel: String,
    @Value("\${app.sender.address:}") private val senderAddress: String,
    @Value("\${app.sender.detail-address:}") private val senderDetailAddress: String,
    @Value("\${app.sender.zip-code:}") private val senderZipCode: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun uploadExcel(file: MultipartFile): Int {
        val workbook = WorkbookFactory.create(file.inputStream)
        val sheet = workbook.getSheetAt(0)

        val orders = mutableListOf<TopSellersOutOrd>()

        // A~N열, 1행 헤더, 2행부터 데이터
        for (rowIdx in 1..sheet.lastRowNum) {
            val row = sheet.getRow(rowIdx) ?: continue
            val no = getCellValue(row, 0)
            if (no.isBlank()) continue

            val brandName = getCellValue(row, 1)  // B: BRAND
            val brand = if (brandName.isNotBlank()) {
                brandRepository.findByName(brandName)
                    ?: brandRepository.save(Brand(name = brandName))
            } else null

            orders.add(
                TopSellersOutOrd(
                    no = no,                              // A: NO
                    brand = brand,                        // B: BRAND
                    imwebOrderNo = getCellValue(row, 2),  // C: 아임웹 주문번호
                    hawbNo = getCellValue(row, 3),        // D: HAWB_NO
                    custNm = getCellValue(row, 4),        // E: cust_nm
                    custAddress = getCellValue(row, 5),   // F: cust_address
                    custTelNo = getCellValue(row, 6),     // G: cust_tel_no
                    zipCode = getCellValue(row, 7),       // H: zip_code
                    allowedItemCode = getCellValue(row, 8),    // I: 허용품목코드
                    price = parseDecimalValue(row, 9),         // J: 가격
                    productType = getCellValue(row, 10),       // K: 상품종류
                                                               // L: 미사용
                    qty = parseIntValue(row, 12),              // M: 수량(QTY)
                    qtyUnit = getCellValue(row, 13)            // N: 물품의 수량단위
                )
            )
        }

        // 합포장 그룹 부여: brand + custNm + custAddress + custTelNo 동일하면 같은 그룹
        // 그룹ID = 첫번째 주문의 아임웹주문번호_C
        val groupMap = mutableMapOf<String, String>()
        for (order in orders) {
            val brandName = order.brand?.name ?: ""
            val key = "${brandName}|${order.custNm ?: ""}|${order.custAddress ?: ""}|${order.custTelNo ?: ""}"
            val groupId = groupMap.getOrPut(key) { "${order.imwebOrderNo ?: order.no ?: ""}_C" }
            order.bundleGroup = groupId
        }

        workbook.close()

        if (orders.isNotEmpty()) {
            topSellersOutOrdRepository.saveAll(orders)
            logger.info("탑셀러 엑셀 업로드 완료: ${orders.size}건 저장")
        }

        return orders.size
    }

    @Transactional
    fun processBarcodeScan(barcodeValue: String): BarcodeScanResult {
        logger.info("바코드 스캔 처리 시작 - 바코드값: $barcodeValue")

        // 1. DB 조회
        val order = topSellersOutOrdRepository.findByNo(barcodeValue)
            ?: return BarcodeScanResult(
                success = false,
                errorType = BarcodeScanErrorType.NOT_FOUND,
                message = "주문번호 #${barcodeValue} 를 찾을 수 없습니다."
            )

        // 2. 이미 송장번호가 있으면 저장된 이미지로 재출력
        if (!order.invoiceNo.isNullOrBlank()) {
            val existingInvoiceNo = order.invoiceNo!!
            val labelPath = findExistingLabelImage(existingInvoiceNo)
            if (labelPath != null) {
                logger.info("기존 송장번호 발견 - 바코드값: $barcodeValue, 운송장번호: $existingInvoiceNo, 재출력")
                return BarcodeScanResult(
                    success = true,
                    message = "기존 운송장 재출력",
                    invoiceNo = existingInvoiceNo,
                    labelImagePath = labelPath,
                    bundleComplete = true,
                    bundleSeq = order.bundleSeq
                )
            }
            logger.warn("기존 송장번호는 있으나 이미지 없음 - 운송장번호: $existingInvoiceNo, 새로 발급합니다.")
        }

        // 3. 현재 건 스캔 처리
        order.scanned = true
        topSellersOutOrdRepository.save(order)

        // 4. 합포장 그룹 확인
        val bundleGroup = order.bundleGroup
        if (bundleGroup != null) {
            val bundleOrders = topSellersOutOrdRepository.findByBundleGroup(bundleGroup)

            // 합포장 순번 부여: 이미 부여된 게 있으면 재사용, 없으면 새 순번
            val existingSeq = bundleOrders.firstOrNull { it.bundleSeq != null }?.bundleSeq
            val bundleSeq: Int
            if (existingSeq != null) {
                bundleSeq = existingSeq
            } else {
                val maxSeq = topSellersOutOrdRepository.findMaxActiveBundleSeq()
                bundleSeq = maxSeq + 1
                bundleOrders.forEach { it.bundleSeq = bundleSeq }
                topSellersOutOrdRepository.saveAll(bundleOrders)
            }

            val bundleTotal = bundleOrders.size
            val bundleScanned = bundleOrders.count { it.scanned }
            val allScanned = bundleTotal == bundleScanned

            if (!allScanned) {
                // 아직 그룹 내 미스캔 건이 있음 - 스캔만 기록하고 대기
                val unscannedNos = bundleOrders.filter { !it.scanned }.map { it.no }
                logger.info("${bundleSeq}번째 합포장 스캔 대기 - 그룹: $bundleGroup, 스캔: $bundleScanned/$bundleTotal")
                return BarcodeScanResult(
                    success = true,
                    message = "${bundleSeq}번째 합포장 - 스캔 완료 ($bundleScanned/$bundleTotal). 남은 건: ${unscannedNos.joinToString(", ")}",
                    bundleGroup = bundleGroup,
                    bundleTotal = bundleTotal,
                    bundleScanned = bundleScanned,
                    bundleComplete = false,
                    bundleSeq = bundleSeq
                )
            }

            // 모든 건 스캔 완료 - 송장 발급 진행
            logger.info("${bundleSeq}번째 합포장 스캔 완료 - 그룹: $bundleGroup, 총 ${bundleTotal}건, 송장 발급 진행")
            return processBundleInvoice(order, bundleOrders, barcodeValue, bundleSeq)
        }

        // 합포장 그룹이 없는 단건 - 바로 송장 발급
        val maxSeq = topSellersOutOrdRepository.findMaxActiveBundleSeq()
        val bundleSeq = maxSeq + 1
        order.bundleSeq = bundleSeq
        topSellersOutOrdRepository.save(order)
        return processBundleInvoice(order, listOf(order), barcodeValue, bundleSeq)
    }

    private fun processBundleInvoice(
        representativeOrder: TopSellersOutOrd,
        bundleOrders: List<TopSellersOutOrd>,
        barcodeValue: String,
        bundleSeq: Int
    ): BarcodeScanResult {
        // 1. 주소정제 API 호출
        val address = representativeOrder.custAddress ?: ""
        val addressResponse = try {
            cjAddressService.refineAddress(address)
        } catch (e: Exception) {
            logger.error("주소정제 API 호출 실패", e)
            return BarcodeScanResult(
                success = false,
                errorType = BarcodeScanErrorType.ADDRESS_REFINE_FAILED,
                message = "주소정제를 실패하였습니다. #${barcodeValue} 의 주소를 확인해주세요"
            )
        }

        if (addressResponse.resultCd != "S" || addressResponse.data == null) {
            return BarcodeScanResult(
                success = false,
                errorType = BarcodeScanErrorType.ADDRESS_REFINE_FAILED,
                message = "주소정제를 실패하였습니다. #${barcodeValue} 의 주소를 확인해주세요"
            )
        }

        val addressData = addressResponse.data

        // 2. 운송장번호 채번
        val invoiceResponse = try {
            cjInvoiceService.issueInvoiceNumber()
        } catch (e: Exception) {
            logger.error("운송장번호 채번 실패", e)
            return BarcodeScanResult(
                success = false,
                errorType = BarcodeScanErrorType.INVOICE_FAILED,
                message = "운송장 생성에 실패하였습니다. 로그를 확인해주세요"
            )
        }

        if (invoiceResponse.resultCd != "S" || invoiceResponse.data == null) {
            return BarcodeScanResult(
                success = false,
                errorType = BarcodeScanErrorType.INVOICE_FAILED,
                message = "운송장 생성에 실패하였습니다. 로그를 확인해주세요"
            )
        }

        val invoiceNo = invoiceResponse.data.invcNo

        // 3. 합포장 상품 정보 합산
        val totalQty = bundleOrders.sumOf { it.qty ?: 1 }
        val productInfoList = bundleOrders.mapNotNull { it.productType }.distinct()
        val productInfo = productInfoList.joinToString(", ")
        val totalAmount = bundleOrders.sumOf { it.price?.toInt() ?: 0 }

        // 4. 라벨 이미지 생성 및 저장
        val labelImagePath: String
        try {
            val receiverPhone = representativeOrder.custTelNo ?: ""
            val receiverName = maskName(representativeOrder.custNm ?: "") + "  " + maskPhone(receiverPhone)
            val receiverAddr1 = representativeOrder.custAddress ?: ""

            val imageBytes = shippingLabelService.generateLabelImage(
                addressData = addressData,
                invoiceNo = invoiceNo,
                sender = senderName,
                phoneNumber = senderTel,
                address1 = senderAddress,
                address2 = "",
                shippingMemo = "",
                receiverName = receiverName,
                receiverAddr1 = receiverAddr1,
                receiverAddr2 = "",
                productInfo = productInfo,
                productQty = totalQty.toString()
            )

            labelImagePath = saveLabelImage(invoiceNo, imageBytes)
        } catch (e: Exception) {
            logger.error("라벨 이미지 생성/저장 실패", e)
            return BarcodeScanResult(
                success = false,
                errorType = BarcodeScanErrorType.LABEL_GENERATION_FAILED,
                message = "운송장 생성에 실패하였습니다. 로그를 확인해주세요"
            )
        }

        // 5. 택배 예약접수
        try {
            val rcvrPhoneParts = splitPhoneNumber(representativeOrder.custTelNo ?: "")
            val sendrPhoneParts = splitPhoneNumber(senderTel)

            val fullRcvrAddr = representativeOrder.custAddress ?: ""
            val (rcvrAddr, rcvrDetailAddr) = splitAddress(fullRcvrAddr)

            val custUseNo = if (bundleOrders.size > 1) {
                bundleOrders.mapNotNull { it.imwebOrderNo }.joinToString("_") + "_C"
            } else {
                representativeOrder.imwebOrderNo ?: representativeOrder.no ?: barcodeValue
            }

            val bookingResponse = cjBookingService.registerBooking(
                custUseNo = custUseNo,
                invcNo = invoiceNo,
                sendrNm = senderName,
                sendrTelNo1 = sendrPhoneParts[0],
                sendrTelNo2 = sendrPhoneParts[1],
                sendrTelNo3 = sendrPhoneParts[2],
                sendrZipNo = senderZipCode,
                sendrAddr = senderAddress,
                sendrDetailAddr = senderDetailAddress,
                rcvrNm = representativeOrder.custNm ?: "",
                rcvrTelNo1 = rcvrPhoneParts[0],
                rcvrTelNo2 = rcvrPhoneParts[1],
                rcvrTelNo3 = rcvrPhoneParts[2],
                rcvrZipNo = representativeOrder.zipCode ?: "",
                rcvrAddr = rcvrAddr,
                rcvrDetailAddr = rcvrDetailAddr,
                gdsNm = productInfo.ifBlank { "상품" },
                gdsQty = totalQty.toString(),
                gdsAmt = totalAmount.toString()
            )

            if (bookingResponse.resultCd != "S") {
                logger.warn("택배 예약접수 실패 - RESULT_CD: ${bookingResponse.resultCd}, DETAIL: ${bookingResponse.resultDetail}")
                return BarcodeScanResult(
                    success = false,
                    errorType = BarcodeScanErrorType.BOOKING_FAILED,
                    message = "택배 예약접수 실패: ${bookingResponse.resultDetail}"
                )
            }
        } catch (e: Exception) {
            logger.error("택배 예약접수 실패", e)
            return BarcodeScanResult(
                success = false,
                errorType = BarcodeScanErrorType.BOOKING_FAILED,
                message = "택배 예약접수 실패: ${e.message}"
            )
        }

        // 6. 성공 - 그룹 내 모든 건에 운송장번호 업데이트
        for (bundleOrder in bundleOrders) {
            bundleOrder.invoiceNo = invoiceNo
        }
        topSellersOutOrdRepository.saveAll(bundleOrders)

        logger.info("바코드 스캔 처리 완료 - 바코드값: $barcodeValue, 운송장번호: $invoiceNo, 합포장: ${bundleOrders.size}건")

        return BarcodeScanResult(
            success = true,
            message = "${bundleSeq}번째 합포장 완료 - 운송장 생성 및 예약접수 완료 (${bundleOrders.size}건)",
            invoiceNo = invoiceNo,
            labelImagePath = labelImagePath,
            bundleGroup = representativeOrder.bundleGroup,
            bundleTotal = bundleOrders.size,
            bundleScanned = bundleOrders.size,
            bundleComplete = true,
            bundleSeq = bundleSeq
        )
    }

    /**
     * 기존 라벨 이미지 파일을 찾아서 상대 경로 반환
     * invoice base path 하위 전체를 검색
     */
    fun findExistingLabelImage(invoiceNo: String): String? {
        val basePath = Paths.get(invoiceBasePath)
        if (!Files.exists(basePath)) return null

        val fileName = "$invoiceNo.png"
        val found = Files.walk(basePath)
            .filter { it.fileName.toString() == fileName }
            .findFirst()

        return if (found.isPresent) {
            basePath.relativize(found.get()).toString()
        } else {
            null
        }
    }

    fun saveLabelImage(invoiceNo: String, imageBytes: ByteArray): String {
        val now = LocalDate.now()
        val year = now.format(DateTimeFormatter.ofPattern("yyyy"))
        val month = now.format(DateTimeFormatter.ofPattern("MM"))
        val day = now.format(DateTimeFormatter.ofPattern("dd"))

        val dirPath = Paths.get(invoiceBasePath, year, month, day)
        Files.createDirectories(dirPath)

        val filePath = dirPath.resolve("$invoiceNo.png")
        Files.write(filePath, imageBytes)
        logger.info("라벨 이미지 저장 완료: $filePath")

        return "$year/$month/$day/$invoiceNo.png"
    }

    fun splitPhoneNumber(phone: String): List<String> {
        val cleaned = phone.replace(" ", "")
        val parts = cleaned.split("-")
        return when {
            parts.size >= 3 -> parts.take(3)
            parts.size == 1 && cleaned.length >= 10 -> {
                listOf(
                    cleaned.substring(0, 3),
                    cleaned.substring(3, cleaned.length - 4),
                    cleaned.substring(cleaned.length - 4)
                )
            }
            else -> listOf(cleaned, "", "")
        }
    }

    /**
     * 주소를 기본주소/상세주소로 분리
     * 괄호가 있으면 괄호 앞까지 기본주소, 괄호 포함 이후 상세주소
     * 괄호가 없으면 전체를 기본주소로, 상세주소는 "-"
     */
    fun splitAddress(fullAddress: String): Pair<String, String> {
        val parenIdx = fullAddress.indexOf("(")
        return if (parenIdx > 0) {
            val base = fullAddress.substring(0, parenIdx).trim()
            val detail = fullAddress.substring(parenIdx).trim()
            Pair(base, detail.ifBlank { "-" })
        } else {
            Pair(fullAddress, "-")
        }
    }

    private fun maskName(name: String): String {
        if (name.length <= 1) return name
        return name.first() + "*" + name.drop(2)
    }

    private fun maskPhone(phone: String): String {
        val parts = splitPhoneNumber(phone)
        if (parts[1].isEmpty()) return phone
        return "${parts[0]}-${parts[1]}-****"
    }

    private fun getCellValue(row: Row, colIdx: Int): String {
        val cell = row.getCell(colIdx) ?: return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.NUMERIC -> {
                val numVal = cell.numericCellValue
                if (numVal == numVal.toLong().toDouble()) {
                    numVal.toLong().toString()
                } else {
                    numVal.toString()
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> cell.stringCellValue.trim()
            else -> ""
        }
    }

    private fun parseDecimalValue(row: Row, colIdx: Int): BigDecimal? {
        val cell = row.getCell(colIdx) ?: return null
        return try {
            when (cell.cellType) {
                CellType.NUMERIC -> BigDecimal.valueOf(cell.numericCellValue)
                CellType.STRING -> {
                    val value = cell.stringCellValue.trim()
                    if (value.isNotBlank()) BigDecimal(value) else null
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseIntValue(row: Row, colIdx: Int): Int? {
        val cell = row.getCell(colIdx) ?: return null
        return try {
            when (cell.cellType) {
                CellType.NUMERIC -> cell.numericCellValue.toInt()
                CellType.STRING -> {
                    val value = cell.stringCellValue.trim()
                    if (value.isNotBlank()) value.toInt() else null
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
