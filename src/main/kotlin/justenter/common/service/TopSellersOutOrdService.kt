package justenter.common.service

import justenter.cjdeliveryapi.service.CjAddressService
import justenter.cjdeliveryapi.service.CjBookingService
import justenter.cjdeliveryapi.service.CjInvoiceService
import justenter.common.dto.BarcodeScanErrorType
import justenter.common.dto.BarcodeScanResult
import justenter.common.entity.TopSellersOutOrd
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

        // A~O열 (0~14), 1행 헤더, 2행부터 데이터
        for (rowIdx in 1..sheet.lastRowNum) {
            val row = sheet.getRow(rowIdx) ?: continue
            val no = getCellValue(row, 0)
            if (no.isBlank()) continue

            orders.add(
                TopSellersOutOrd(
                    no = no,                              // A: NO
                    imwebOrderNo = getCellValue(row, 1),  // B: 아임웹 주문번호
                    hawbNo = getCellValue(row, 2),        // C: HAWB_NO
                    cjNo = getCellValue(row, 3),          // D: CJ_NO
                    custNm = getCellValue(row, 4),        // E: cust_nm
                    custAddress = getCellValue(row, 5),   // F: cust_address
                    custTelNo = getCellValue(row, 6),     // G: cust_tel_no
                    zipCode = getCellValue(row, 7),       // H: zip_code
                    consigneeIdCardNo = getCellValue(row, 8),  // I: ConsigneeIdCardNo
                    allowedItemCode = getCellValue(row, 9),    // J: 허용품목코드
                    price = parseDecimalValue(row, 10),        // K: 가격
                    productType = getCellValue(row, 11),       // L: 상품종류
                    brand = getCellValue(row, 12),             // M: BRAND
                    qty = parseIntValue(row, 13),              // N: 수량(QTY)
                    qtyUnit = getCellValue(row, 14)            // O: 물품의 수량단위
                )
            )
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
        if (!order.cjNo.isNullOrBlank()) {
            val existingInvoiceNo = order.cjNo!!
            val labelPath = findExistingLabelImage(existingInvoiceNo)
            if (labelPath != null) {
                logger.info("기존 송장번호 발견 - 바코드값: $barcodeValue, 운송장번호: $existingInvoiceNo, 재출력")
                return BarcodeScanResult(
                    success = true,
                    message = "기존 운송장 재출력",
                    invoiceNo = existingInvoiceNo,
                    labelImagePath = labelPath
                )
            }
            logger.warn("기존 송장번호는 있으나 이미지 없음 - 운송장번호: $existingInvoiceNo, 새로 발급합니다.")
        }

        // 3. 주소정제 API 호출
        val address = order.custAddress ?: ""
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

        // 3. 운송장번호 채번
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

        // 4. 라벨 이미지 생성 및 저장
        val labelImagePath: String
        try {
            val receiverPhone = order.custTelNo ?: ""
            val receiverName = maskName(order.custNm ?: "") + "  " + maskPhone(receiverPhone)
            val receiverAddr1 = order.custAddress ?: ""
            val productInfo = order.productType ?: ""
            val productQty = (order.qty ?: 1).toString()

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
                productQty = productQty
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
            val rcvrPhoneParts = splitPhoneNumber(order.custTelNo ?: "")
            val sendrPhoneParts = splitPhoneNumber(senderTel)

            // 받는분 주소 파싱: 괄호 앞까지 기본주소, 나머지 상세주소
            val fullRcvrAddr = order.custAddress ?: ""
            val (rcvrAddr, rcvrDetailAddr) = splitAddress(fullRcvrAddr)

            val bookingResponse = cjBookingService.registerBooking(
                custUseNo = order.imwebOrderNo ?: order.no ?: barcodeValue,
                invcNo = invoiceNo,
                sendrNm = senderName,
                sendrTelNo1 = sendrPhoneParts[0],
                sendrTelNo2 = sendrPhoneParts[1],
                sendrTelNo3 = sendrPhoneParts[2],
                sendrZipNo = senderZipCode,
                sendrAddr = senderAddress,
                sendrDetailAddr = senderDetailAddress,
                rcvrNm = order.custNm ?: "",
                rcvrTelNo1 = rcvrPhoneParts[0],
                rcvrTelNo2 = rcvrPhoneParts[1],
                rcvrTelNo3 = rcvrPhoneParts[2],
                rcvrZipNo = order.zipCode ?: "",
                rcvrAddr = rcvrAddr,
                rcvrDetailAddr = rcvrDetailAddr,
                gdsNm = order.productType ?: "상품",
                gdsQty = (order.qty ?: 1).toString(),
                gdsAmt = (order.price?.toInt() ?: 0).toString()
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

        // 6. 성공 - CJ 운송장번호 업데이트
        order.cjNo = invoiceNo
        topSellersOutOrdRepository.save(order)

        logger.info("바코드 스캔 처리 완료 - 바코드값: $barcodeValue, 운송장번호: $invoiceNo")

        return BarcodeScanResult(
            success = true,
            message = "운송장 생성 및 예약접수 완료",
            invoiceNo = invoiceNo,
            labelImagePath = labelImagePath
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
