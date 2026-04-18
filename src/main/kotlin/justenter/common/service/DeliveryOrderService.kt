package justenter.common.service

import justenter.common.entity.DeliveryOrder
import justenter.common.repository.DeliveryOrderRepository
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
class DeliveryOrderService(
    private val deliveryOrderRepository: DeliveryOrderRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 엑셀 파일을 파싱하여 DB에 저장
     * 1행은 헤더, 2행부터 데이터
     * A~R열(18개 컬럼)을 읽어서 delivery_order 테이블에 저장
     */
    @Transactional
    fun uploadExcel(file: MultipartFile): Int {
        val workbook = WorkbookFactory.create(file.inputStream)
        val sheet = workbook.getSheetAt(0)

        val orders = mutableListOf<DeliveryOrder>()

        // 2행부터 데이터 읽기 (index 1부터)
        for (rowIdx in 1..sheet.lastRowNum) {
            val row = sheet.getRow(rowIdx) ?: continue

            // A열(NO)이 비어있으면 스킵
            val no = getCellValue(row, 0)
            if (no.isBlank()) continue

            orders.add(
                DeliveryOrder(
                    no = no,
                    imwebOrderNo = getCellValue(row, 1),
                    hawbNo = getCellValue(row, 2),
                    cjNo = getCellValue(row, 3),
                    consigneeName = getCellValue(row, 4),
                    consigneeAddress = getCellValue(row, 5),
                    consigneeTel = getCellValue(row, 6),
                    zipCode = getCellValue(row, 7),
                    consigneeIdCardNo = getCellValue(row, 8),
                    itemCode = getCellValue(row, 9),
                    totalAmount = getCellValue(row, 10),
                    description = getCellValue(row, 11),
                    brand = getCellValue(row, 12),
                    qty = getCellValue(row, 13),
                    qtyUnit = getCellValue(row, 14),
                    grossWeight = getCellValue(row, 15),
                    currency = getCellValue(row, 16),
                    invoiceValue = getCellValue(row, 17)
                )
            )
        }

        workbook.close()

        if (orders.isNotEmpty()) {
            deliveryOrderRepository.saveAll(orders)
            logger.info("엑셀 업로드 완료: ${orders.size}건 저장")
        }

        return orders.size
    }

    private fun getCellValue(row: Row, colIdx: Int): String {
        val cell = row.getCell(colIdx) ?: return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.NUMERIC -> {
                val numVal = cell.numericCellValue
                // 정수인 경우 소수점 제거
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

    @Transactional(readOnly = true)
    fun findAll(): List<DeliveryOrder> {
        return deliveryOrderRepository.findAll()
    }
}
