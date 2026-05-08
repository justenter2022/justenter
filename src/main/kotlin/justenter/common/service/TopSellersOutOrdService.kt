package justenter.common.service

import justenter.cjdeliveryapi.service.CjAddressService
import justenter.cjdeliveryapi.service.CjBookingService
import justenter.cjdeliveryapi.service.CjInvoiceService
import justenter.common.dto.BarcodeScanErrorType
import justenter.common.dto.BarcodeScanResult
import justenter.common.dto.ExcelUploadResult
import justenter.common.dto.GoodsItem
import justenter.common.entity.Brand
import justenter.common.entity.TopSellersOutOrd
import justenter.common.repository.BrandRepository
import justenter.common.repository.ImwebOrderSectionRepository
import justenter.common.repository.TopSellersOutOrdRepository
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Service
class TopSellersOutOrdService(
    private val topSellersOutOrdRepository: TopSellersOutOrdRepository,
    private val imwebOrderSectionRepository: ImwebOrderSectionRepository,
    private val brandRepository: BrandRepository,
    private val cjAddressService: CjAddressService,
    private val cjInvoiceService: CjInvoiceService,
    private val cjBookingService: CjBookingService,
    private val shippingLabelService: ShippingLabelService,
    private val resourceLoader: ResourceLoader,
    @Value("\${app.invoice.base-path:./invoice}") private val invoiceBasePath: String,
    @Value("\${app.sender.name:}") private val senderName: String,
    @Value("\${app.sender.tel:}") private val senderTel: String,
    @Value("\${app.sender.address:}") private val senderAddress: String,
    @Value("\${app.sender.detail-address:}") private val senderDetailAddress: String,
    @Value("\${app.sender.zip-code:}") private val senderZipCode: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun uploadExcel(file: MultipartFile): ExcelUploadResult {
        val workbook = WorkbookFactory.create(file.inputStream)
        val sheet = workbook.getSheetAt(0)

        val orders = mutableListOf<TopSellersOutOrd>()
        val validationErrors = mutableListOf<String>()

        // 엑셀의 모든 no를 먼저 수집하여 이미 DB에 존재하는 no는 제외
        val excelNos = (1..sheet.lastRowNum)
            .mapNotNull { sheet.getRow(it) }
            .map { getCellValue(it, 0) }
            .filter { it.isNotBlank() }
            .toSet()
        val existingNos = if (excelNos.isNotEmpty()) {
            topSellersOutOrdRepository.findByNoIn(excelNos).mapNotNull { it.no }.toSet()
        } else emptySet()

        // 필수 입력 항목 검증 (빈칸이 있으면 결과창에 알림)
        val requiredColumns = listOf(
            0 to "no",
            1 to "브랜드명",
            2 to "아임웹 주문번호",
            4 to "C/NAME(KOR)",
            5 to "C/ADDRESS(KOR)",
            6 to "C/TEL NO",
            7 to "ZIP CODE"
        )
        for (rowIdx in 1..sheet.lastRowNum) {
            val row = sheet.getRow(rowIdx) ?: continue
            val missing = requiredColumns
                .filter { (col, _) -> getCellValue(row, col).isBlank() }
                .map { it.second }
            if (missing.isNotEmpty()) {
                validationErrors.add("${rowIdx + 1}행: ${missing.joinToString(", ")} 없음")
            }
        }

        // hawbNo 검증 (hawb_no 는 row 유니크)
        val hawbWarnings = mutableListOf<String>()
        // 엑셀 row 수집: hawb → [no]
        val excelNosByHawb = mutableMapOf<String, MutableList<String>>()
        for (rowIdx in 1..sheet.lastRowNum) {
            val row = sheet.getRow(rowIdx) ?: continue
            val no = getCellValue(row, 0)
            val hawbNo = getCellValue(row, 3)
            if (no.isBlank() || hawbNo.isBlank()) continue
            excelNosByHawb.getOrPut(hawbNo) { mutableListOf() }.add(no)
        }
        // (A) 파일 내 hawb 중복 → 경고 + 해당 hawb 의 row 전부 저장 제외
        val duplicateHawbsInFile = excelNosByHawb.filter { it.value.size > 1 }.keys
        for (hawb in duplicateHawbsInFile) {
            val nos = excelNosByHawb[hawb]!!
            hawbWarnings.add(
                "엑셀업로드 파일에 T송장: ${hawb} 이 여러 row (no: ${nos.joinToString(", ")}) 에 존재합니다. 파일을 확인 후 다시 업로드해주세요"
            )
        }
        // (B) DB vs 파일: 파일의 hawbNo 가 DB 에 이미 다른 `no` 로 등록됨
        val dbOrdersByHawb = if (excelNosByHawb.isNotEmpty()) {
            topSellersOutOrdRepository.findByHawbNoIn(excelNosByHawb.keys)
                .associateBy { it.hawbNo ?: "" }
        } else emptyMap()
        for ((hawb, excelNos) in excelNosByHawb) {
            val dbOrder = dbOrdersByHawb[hawb] ?: continue
            val dbNo = dbOrder.no ?: ""
            for (excelNo in excelNos) {
                if (excelNo != dbNo) {
                    hawbWarnings.add(
                        "이미 등록된 T송장: ${hawb} 이 no: ${dbNo} 으로 존재하는데, " +
                            "엑셀업로드 파일에 no: ${excelNo} 으로 있습니다. 확인해주세요"
                    )
                }
            }
        }

        // A~N열, 1행 헤더, 2행부터 데이터
        for (rowIdx in 1..sheet.lastRowNum) {
            val row = sheet.getRow(rowIdx) ?: continue
            val no = getCellValue(row, 0)
            if (no.isBlank()) continue
            if (no in existingNos) continue
            val hawbNo = getCellValue(row, 3)
            if (hawbNo in duplicateHawbsInFile) continue

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
        // 2건 이상 묶음에만 bundleGroup/bundleSeq 를 부여. 단건은 null 유지.
        val keyToOrders = mutableMapOf<String, MutableList<TopSellersOutOrd>>()
        for (order in orders) {
            val brandName = order.brand?.name ?: ""
            val key = "${brandName}|${order.custNm ?: ""}|${order.custAddress ?: ""}|${order.custTelNo ?: ""}"
            keyToOrders.getOrPut(key) { mutableListOf() }.add(order)
        }
        val bundleGroupsInBatch = keyToOrders.values.filter { it.size >= 2 }
        for (groupOrders in bundleGroupsInBatch) {
            val first = groupOrders.first()
            val groupId = "${first.imwebOrderNo ?: first.no ?: ""}_C"
            groupOrders.forEach { it.bundleGroup = groupId }
        }

        // 기존 DB 의 bundleGroup 과 충돌 방지: 동일 이름 존재하면 _2, _3 ... suffix 부여
        val existingGroups = topSellersOutOrdRepository.findAllDistinctBundleGroups().toMutableSet()
        val baseGroupIds = orders.mapNotNull { it.bundleGroup }.toSet()
        val groupRemap = mutableMapOf<String, String>()
        for (baseId in baseGroupIds) {
            if (baseId !in existingGroups) {
                existingGroups.add(baseId)
                continue
            }
            var suffix = 2
            var candidate = "${baseId}_$suffix"
            while (candidate in existingGroups) {
                suffix++
                candidate = "${baseId}_$suffix"
            }
            groupRemap[baseId] = candidate
            existingGroups.add(candidate)
            logger.info("bundleGroup 충돌 회피: $baseId -> $candidate")
        }
        if (groupRemap.isNotEmpty()) {
            for (order in orders) {
                val remapped = groupRemap[order.bundleGroup]
                if (remapped != null) order.bundleGroup = remapped
            }
        }

        // 합포장 순번(bundleSeq) 부여: 2건 이상 묶음에만. 단건은 null 유지.
        // 금일마감 후 해제된 번호는 재사용하되, 진행중/대기중 묶음의 번호는 유지
        val usedSeqs = topSellersOutOrdRepository.findAllUsedBundleSeqs().toMutableSet()
        val bundledOrdersByGroup = orders.filter { it.bundleGroup != null }.groupBy { it.bundleGroup!! }
        for ((_, groupOrders) in bundledOrdersByGroup) {
            val seq = nextAvailableBundleSeq(usedSeqs)
            usedSeqs.add(seq)
            groupOrders.forEach { it.bundleSeq = seq }
        }

        workbook.close()

        if (orders.isNotEmpty()) {
            topSellersOutOrdRepository.saveAll(orders)
            logger.info("탑셀러 엑셀 업로드 완료: ${orders.size}건 저장")
        }

        // 오늘 업로드한 합포장 통계 (단건 제외)
        val todayBundleMap = orders.filter { it.bundleGroup != null }.groupBy { it.bundleGroup!! }
        val todayBundleGroups = todayBundleMap.count { it.value.size > 1 }
        val todayBundleItems = todayBundleMap.filter { it.value.size > 1 }.values.sumOf { it.size }

        // 기존 잔여 합포장 (invoiceNo 없는 것 중 오늘 업로드 제외, 단건 제외)
        val todayOrderIds = orders.map { it.id }.toSet()
        val remaining = topSellersOutOrdRepository.findByInvoiceNoIsNull()
            .filter { it.id !in todayOrderIds && it.bundleGroup != null }
        val remainingBundleMap = remaining.groupBy { it.bundleGroup!! }
        val remainingBundleGroups = remainingBundleMap.count { it.value.size > 1 }
        val remainingBundleItems = remainingBundleMap.filter { it.value.size > 1 }.values.sumOf { it.size }

        return ExcelUploadResult(
            totalCount = excelNos.size,
            savedCount = orders.size,
            skippedCount = existingNos.size,
            validationErrors = validationErrors,
            hawbWarnings = hawbWarnings,
            todayBundleGroups = todayBundleGroups,
            todayBundleItems = todayBundleItems,
            remainingBundleGroups = remainingBundleGroups,
            remainingBundleItems = remainingBundleItems
        )
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

        // 3. 중복 스캔 방지: 이미 스캔된 주문이 다시 들어오면 처리 중단하고 남은 건 안내
        if (order.scanned) {
            val bundleGroup = order.bundleGroup
            val bundleOrders = if (bundleGroup != null) {
                topSellersOutOrdRepository.findActiveByBundleGroup(bundleGroup)
            } else listOf(order)
            val unscannedNos = bundleOrders.filter { !it.scanned }.map { it.no }
            val bundleSeq = bundleOrders.firstOrNull { it.bundleSeq != null }?.bundleSeq
            val message = if (unscannedNos.isEmpty()) {
                "이미 스캔된 주문입니다: #${barcodeValue}"
            } else {
                "이미 스캔된 주문입니다: #${barcodeValue}. 남은 스캔: ${unscannedNos.joinToString(", ")}"
            }
            logger.info("중복 스캔 차단 - 바코드값: $barcodeValue, 남은 건: ${unscannedNos.joinToString(", ")}")
            return BarcodeScanResult(
                success = true,
                message = message,
                bundleGroup = bundleGroup,
                bundleTotal = bundleOrders.size,
                bundleScanned = bundleOrders.count { it.scanned },
                bundleComplete = false,
                bundleSeq = bundleSeq
            )
        }

        // 4. 현재 건 스캔 처리
        order.scanned = true
        topSellersOutOrdRepository.save(order)

        // 5. 합포장 그룹 확인
        val bundleGroup = order.bundleGroup
        if (bundleGroup != null) {
            val bundleOrders = topSellersOutOrdRepository.findActiveByBundleGroup(bundleGroup)

            // bundleSeq 는 엑셀 업로드 시 부여됨. null 인 레거시 데이터만 여기서 보정.
            val bundleSeq = ensureBundleSeq(bundleOrders)

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

        // 합포장 그룹이 없는 단건 - 바로 송장 발급 (bundleSeq 없음)
        return processBundleInvoice(order, listOf(order), barcodeValue, null)
    }

    /**
     * 합포장 주문 목록에 bundleSeq 를 보장한다.
     * 이미 부여돼 있으면 그대로 재사용, 없으면 사용 가능한 가장 작은 번호를 새로 부여하고 저장.
     */
    private fun ensureBundleSeq(bundleOrders: List<TopSellersOutOrd>): Int {
        val existing = bundleOrders.firstOrNull { it.bundleSeq != null }?.bundleSeq
        if (existing != null) {
            // 동일 그룹 내 일부에만 seq 가 있을 수 있으므로 일관성 보정
            bundleOrders.filter { it.bundleSeq == null }.forEach { it.bundleSeq = existing }
            if (bundleOrders.any { it.bundleSeq != existing }) {
                topSellersOutOrdRepository.saveAll(bundleOrders)
            }
            return existing
        }
        val usedSeqs = topSellersOutOrdRepository.findAllUsedBundleSeqs().toMutableSet()
        val seq = nextAvailableBundleSeq(usedSeqs)
        bundleOrders.forEach { it.bundleSeq = seq }
        topSellersOutOrdRepository.saveAll(bundleOrders)
        return seq
    }

    private fun nextAvailableBundleSeq(usedSeqs: Set<Int>): Int {
        var candidate = 1
        while (candidate in usedSeqs) candidate++
        return candidate
    }

    /**
     * 금일마감: 송장 발급이 완료된 묶음의 bundleSeq 를 해제하여 다음 업로드에서 재사용 가능하게 한다.
     * 진행중(일부 스캔)이거나 대기중(스캔 전)인 묶음의 seq 는 유지된다.
     */
    @Transactional
    fun closeToday(): Int {
        val completed = topSellersOutOrdRepository.findCompletedBundleOrdersWithSeq()
        if (completed.isEmpty()) return 0
        val releasedSeqs = completed.mapNotNull { it.bundleSeq }.toSet()
        completed.forEach { it.bundleSeq = null }
        topSellersOutOrdRepository.saveAll(completed)
        logger.info("금일마감: ${releasedSeqs.size}개 합포장 순번 해제 (seq: ${releasedSeqs.sorted()})")
        return releasedSeqs.size
    }

    private fun processBundleInvoice(
        representativeOrder: TopSellersOutOrd,
        bundleOrders: List<TopSellersOutOrd>,
        barcodeValue: String,
        bundleSeq: Int?
    ): BarcodeScanResult {
        // 0. wrk_stat 검증: 묶음 내 모든 건이 20(주문수집) 상태여야 함
        val invalidStatOrders = bundleOrders.filter { it.wrkStat != TopSellersOutOrd.WRK_STAT_COLLECTED }
        if (invalidStatOrders.isNotEmpty()) {
            val invalidInfo = invalidStatOrders.joinToString(", ") { "#${it.no}(wrk_stat=${it.wrkStat})" }
            logger.warn("송장 발급 불가 - wrk_stat 20(주문수집) 이 아닌 건: $invalidInfo")
            return BarcodeScanResult(
                success = false,
                errorType = BarcodeScanErrorType.INVOICE_FAILED,
                message = "송장 발급 불가: 주문수집(20) 상태가 아닌 건이 있습니다 - $invalidInfo"
            )
        }

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
        val goodsItems = bundleOrders
            .groupBy { it.productType ?: "" }
            .map { (productType, group) ->
                GoodsItem(
                    productType = productType,
                    qty = group.sumOf { it.qty ?: 1 }.toString(),
                    amount = group.sumOf { it.price?.toInt() ?: 0 }.toString()
                )
            }

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
                products = goodsItems
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
                (bundleOrders.firstOrNull()?.imwebOrderNo ?: "") + "_C"
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
                goods = goodsItems
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

        // 6. 성공 - 그룹 내 모든 건에 운송장번호 업데이트 및 wrk_stat 20 → 30 전환
        val issuedAt = LocalDateTime.now()
        for (bundleOrder in bundleOrders) {
            bundleOrder.invoiceNo = invoiceNo
            bundleOrder.invoiceIssuedAt = issuedAt
            bundleOrder.wrkStat = TopSellersOutOrd.WRK_STAT_INVOICE_ISSUED
        }
        topSellersOutOrdRepository.saveAll(bundleOrders)

        logger.info("바코드 스캔 처리 완료 - 바코드값: $barcodeValue, 운송장번호: $invoiceNo, 합포장: ${bundleOrders.size}건")

        val successMessage = if (bundleSeq != null) {
            "${bundleSeq}번째 합포장 완료 - 운송장 생성 및 예약접수 완료 (${bundleOrders.size}건)"
        } else {
            "운송장 생성 및 예약접수 완료"
        }
        return BarcodeScanResult(
            success = true,
            message = successMessage,
            invoiceNo = invoiceNo,
            labelImagePath = labelImagePath,
            bundleGroup = representativeOrder.bundleGroup,
            bundleTotal = bundleOrders.size,
            bundleScanned = bundleOrders.size,
            bundleComplete = true,
            bundleSeq = bundleSeq
        )
    }

    fun generateFeedbackExcel(): String {
        val unprocessed = topSellersOutOrdRepository.findByInvoiceNoIsNull()

        val now = LocalDate.now()
        val year = now.format(DateTimeFormatter.ofPattern("yyyy"))
        val month = now.format(DateTimeFormatter.ofPattern("MM"))
        val day = now.format(DateTimeFormatter.ofPattern("dd"))

        val dirPath = Paths.get("src/main/resources/feedback", year, month, day)
        Files.createDirectories(dirPath)

        val fileName = "feedback_${year}${month}${day}.xlsx"
        val filePath = dirPath.resolve(fileName)

        val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook()
        val sheet = workbook.createSheet("미출력 주문")

        // 헤더
        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).setCellValue("NO")
        headerRow.createCell(1).setCellValue("고객명")
        headerRow.createCell(2).setCellValue("주소")
        headerRow.createCell(3).setCellValue("전화번호")

        // 데이터
        for ((idx, order) in unprocessed.withIndex()) {
            val row = sheet.createRow(idx + 1)
            row.createCell(0).setCellValue(order.no ?: "")
            row.createCell(1).setCellValue(order.custNm ?: "")
            row.createCell(2).setCellValue(order.custAddress ?: "")
            row.createCell(3).setCellValue(order.custTelNo ?: "")
        }

        // 컬럼 너비 자동 조정
        for (i in 0..3) sheet.autoSizeColumn(i)

        Files.newOutputStream(filePath).use { workbook.write(it) }
        workbook.close()

        logger.info("피드백 엑셀 생성 완료: $filePath (${unprocessed.size}건)")
        return filePath.toString()
    }

    data class ImwebInvoiceExcelResult(
        val fileName: String,
        val bytes: ByteArray,
        val warnings: List<String>,
        val rowCount: Int,
        val contentType: String
    )

    companion object {
        private const val XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        private const val ZIP_CONTENT_TYPE = "application/zip"
    }

    fun listBrands(): List<Brand> {
        return brandRepository.findAll().sortedBy { it.name }
    }

    /**
     * 아임웹 송장일괄등록 양식 엑셀 생성
     * brandId 가 null 이면 해당 날짜의 모든 브랜드 주문을 브랜드별 xlsx 로 만들어 ZIP 으로 묶어 반환한다.
     * brandId 가 지정되면 해당 브랜드 주문만 단일 xlsx 로 반환한다.
     * 다운로드 대상에 포함된 주문 중 wrkStat 이 30(송장발급)인 것은 31(송장발송)로 전환한다.
     * (이미 31인 것도 다운로드 대상에는 포함시킨다.)
     */
    @Transactional
    fun generateImwebInvoiceExcel(date: LocalDate, brandId: Long?): ImwebInvoiceExcelResult {
        val start = LocalDateTime.of(date, LocalTime.MIN)
        val end = LocalDateTime.of(date, LocalTime.MAX)
        val dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))

        if (brandId != null) {
            val brandName = brandRepository.findById(brandId).orElse(null)?.name ?: "브랜드${brandId}"
            val orders = topSellersOutOrdRepository.findIssuedBetweenByBrand(start, end, brandId)
            val (bytes, warnings, rowCount) = buildImwebInvoiceExcelBytes(orders)
            val fileName = "아임웹송장일괄등록_${sanitizeFileName(brandName)}_${dateStr}.xlsx"
            logger.info("아임웹 송장 엑셀 생성 완료(단일): $fileName (작성 ${rowCount}행, 원본 ${orders.size}건)")
            promoteWrkStatToSent(orders)
            return ImwebInvoiceExcelResult(fileName, bytes, warnings, rowCount, XLSX_CONTENT_TYPE)
        }

        // 전체: 브랜드별로 나눠 ZIP 으로 묶음
        val orders = topSellersOutOrdRepository.findIssuedBetween(start, end)
        val ordersByBrand = orders.groupBy { it.brand }
        val allWarnings = mutableListOf<String>()
        var totalRows = 0

        val zipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                for ((brand, brandOrders) in ordersByBrand) {
                    val brandName = brand?.name?.ifBlank { null } ?: "브랜드없음"
                    val (xlsxBytes, warnings, rowCount) = buildImwebInvoiceExcelBytes(brandOrders)
                    val entryName = "아임웹송장일괄등록_${sanitizeFileName(brandName)}_${dateStr}.xlsx"
                    zos.putNextEntry(ZipEntry(entryName))
                    zos.write(xlsxBytes)
                    zos.closeEntry()
                    allWarnings.addAll(warnings.map { "[$brandName] $it" })
                    totalRows += rowCount
                }
            }
            baos.toByteArray()
        }

        val fileName = "아임웹송장일괄등록_전체_${dateStr}.zip"
        logger.info("아임웹 송장 엑셀 생성 완료(전체): $fileName (브랜드 ${ordersByBrand.size}개, 작성 ${totalRows}행)")
        promoteWrkStatToSent(orders)
        return ImwebInvoiceExcelResult(fileName, zipBytes, allWarnings, totalRows, ZIP_CONTENT_TYPE)
    }

    /**
     * 다운로드 대상 주문 중 wrkStat 이 30(송장발급)인 것만 31(송장발송)로 승격한다.
     * 이미 31인 것은 그대로 둔다.
     */
    private fun promoteWrkStatToSent(orders: List<TopSellersOutOrd>) {
        val toPromote = orders.filter { it.wrkStat == TopSellersOutOrd.WRK_STAT_INVOICE_ISSUED }
        if (toPromote.isEmpty()) return
        toPromote.forEach { it.wrkStat = TopSellersOutOrd.WRK_STAT_INVOICE_SENT }
        topSellersOutOrdRepository.saveAll(toPromote)
        logger.info("아임웹 송장 다운로드: wrkStat 30->31 전환 ${toPromote.size}건")
    }

    /**
     * 주어진 주문 리스트로 아임웹 송장 양식 xlsx 바이트 생성
     */
    private data class XlsxBuildResult(val bytes: ByteArray, val warnings: List<String>, val rowCount: Int)

    private fun buildImwebInvoiceExcelBytes(orders: List<TopSellersOutOrd>): XlsxBuildResult {
        val templateResource = resourceLoader.getResource("classpath:excel-templates/imweb_invoice_bulk_upload.xlsx")
        val workbook = templateResource.inputStream.use { XSSFWorkbook(it) }
        val sheet = workbook.getSheetAt(0)

        // 3행(index 2)의 스타일을 샘플로 캡처 후 기존 데이터 행 제거
        val styleRow = sheet.getRow(2)
        val sampleStyles = (0..5).map { styleRow?.getCell(it)?.cellStyle }
        val lastRow = sheet.lastRowNum
        for (i in lastRow downTo 2) {
            sheet.getRow(i)?.let { sheet.removeRow(it) }
        }

        val warnings = mutableListOf<String>()

        // (imwebOrderNo, hawbNo) 기준으로 top_sellers_out_ord 내 중복 확인 (orders 범위)
        val topSellerDuplicateKeys = orders
            .filter { !it.imwebOrderNo.isNullOrBlank() && !it.hawbNo.isNullOrBlank() }
            .groupBy { it.imwebOrderNo!! to it.hawbNo!! }
            .filter { it.value.size > 1 }
            .keys
        for ((imwebOrderNo, hawbNo) in topSellerDuplicateKeys) {
            warnings.add(
                "아임웹주문번호: ${imwebOrderNo}, T송장: ${hawbNo} 이 top_seller 엑셀에 여러개가 있습니다."
            )
        }

        // (imwebOrderNo, hawbNo) 기준으로 imweb_order_section 조회 (invoice_no 가 아직 없는 건만)
        val imwebOrderNos = orders.mapNotNull { it.imwebOrderNo }.filter { it.isNotBlank() }.toSet()
        val hawbNos = orders.mapNotNull { it.hawbNo }.filter { it.isNotBlank() }.toSet()
        val sections = if (imwebOrderNos.isNotEmpty() && hawbNos.isNotEmpty()) {
            imwebOrderSectionRepository.findUnInvoicedByImwebOrderNoInAndHawbNoIn(imwebOrderNos, hawbNos)
        } else emptyList()
        val sectionsByKey: Map<Pair<String, String>, List<String>> = sections
            .filter { !it.imwebOrderNo.isNullOrBlank() && !it.hawbNo.isNullOrBlank() }
            .groupBy { it.imwebOrderNo!! to it.hawbNo!! }
            .mapValues { entry -> entry.value.map { it.imwebOrderSectionNo } }

        // imweb_order_section 내에서 (imwebOrderNo, hawbNo) 가 여러개인 키 수집
        val sectionDuplicateKeys = sectionsByKey.filter { it.value.size > 1 }.keys
        for ((imwebOrderNo, hawbNo) in sectionDuplicateKeys) {
            warnings.add(
                "아임웹주문번호: ${imwebOrderNo}, T송장: ${hawbNo} 이 imweb_order_section 엑셀에 여러개가 있습니다."
            )
        }

        // 3행(index 2)부터 데이터 기록. (imwebOrderNo, hawbNo) 가 top_seller/section 에서 중복이면 해당 건 스킵
        var rowIdx = 2
        var skippedNoMatch = 0
        var skippedDuplicate = 0
        for (order in orders) {
            val key = (order.imwebOrderNo ?: "") to (order.hawbNo ?: "")
            if (key in topSellerDuplicateKeys || key in sectionDuplicateKeys) {
                skippedDuplicate++
                continue
            }
            val sectionNos = sectionsByKey[key].orEmpty()
            if (sectionNos.isEmpty()) {
                skippedNoMatch++
                logger.warn("아임웹 송장 양식: 매칭되는 주문섹션 없음 - imwebOrderNo=${order.imwebOrderNo}, hawbNo=${order.hawbNo}, invoiceNo=${order.invoiceNo}")
                continue
            }
            val sectionNo = sectionNos.first()
            val row = sheet.createRow(rowIdx++)
            listOf(
                0 to sectionNo,                     // A: 주문섹션번호
                1 to "",                            // B: 주문섹션품목번호
                2 to "",                            // C: 수량
                3 to "CJ대한통운",                   // D: 택배사
                4 to (order.invoiceNo ?: ""),       // E: 송장번호
                5 to "배송중"                        // F: 주문 상태 변경
            ).forEach { (col, value) ->
                val cell = row.createCell(col)
                cell.setCellValue(value)
                sampleStyles[col]?.let { cell.cellStyle = it }
            }
        }
        if (skippedNoMatch > 0) {
            logger.warn("아임웹 송장 양식: 주문섹션 매칭 실패로 ${skippedNoMatch}건 제외됨")
        }
        if (skippedDuplicate > 0) {
            logger.warn("아임웹 송장 양식: (imwebOrderNo, hawbNo) 중복으로 ${skippedDuplicate}건 제외됨")
        }

        val bytes = ByteArrayOutputStream().use { baos ->
            workbook.write(baos)
            baos.toByteArray()
        }
        workbook.close()

        return XlsxBuildResult(bytes, warnings, rowIdx - 2)
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
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
