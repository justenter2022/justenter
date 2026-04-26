package justenter.common.service

import justenter.common.entity.ImwebOrderSection
import justenter.common.repository.ImwebOrderSectionRepository
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime

data class ImwebOrderSectionUploadResult(
    val totalCount: Int,
    val savedCount: Int,
    val skippedCount: Int,
    val validationErrors: List<String>,
    val hawbWarnings: List<String>
)

class InvalidUploadFormatException(message: String) : RuntimeException(message)

@Service
class ImwebOrderSectionService(
    private val imwebOrderSectionRepository: ImwebOrderSectionRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun uploadExcel(file: MultipartFile, userId: String = "system"): ImwebOrderSectionUploadResult {
        val workbook = WorkbookFactory.create(file.inputStream)
        val sheet = workbook.getSheetAt(0)

        // 헤더(1행) 검증: A=주문번호, D=배송송장번호, E=주문섹션번호
        val headerRow = sheet.getRow(0)
        if (headerRow == null) {
            workbook.close()
            throw InvalidUploadFormatException("업로드양식을 확인해주세요. (1행이 비어있음)")
        }
        val expectedHeaders = listOf(
            Triple(0, "A", "주문번호"),
            Triple(3, "D", "배송송장번호"),
            Triple(4, "E", "주문섹션번호")
        )
        val mismatches = expectedHeaders.mapNotNull { (col, label, expected) ->
            val actual = normalizeHeader(getCellValue(headerRow, col))
            if (actual != expected) {
                "${label}열: '${expected}' 기대, 실제='${if (actual.isBlank()) "(빈값)" else actual}'"
            } else null
        }
        if (mismatches.isNotEmpty()) {
            workbook.close()
            throw InvalidUploadFormatException(
                "업로드양식을 확인해주세요.\n- " + mismatches.joinToString("\n- ")
            )
        }

        // 엑셀의 모든 주문섹션번호(E열)를 수집
        val excelSectionNos = (1..sheet.lastRowNum)
            .mapNotNull { sheet.getRow(it) }
            .map { getCellValue(it, 4) }
            .filter { it.isNotBlank() }
            .toSet()

        val existingSections = if (excelSectionNos.isNotEmpty()) {
            imwebOrderSectionRepository.findByImwebOrderSectionNoIn(excelSectionNos)
        } else emptyList()
        val existingSectionMap = existingSections.associateBy { it.imwebOrderSectionNo }
        val existingSectionNos = existingSectionMap.keys

        val requiredColumns = listOf(
            0 to "아임웹 주문번호",
            4 to "주문섹션번호"
        )
        val validationErrors = mutableListOf<String>()
        for (rowIdx in 1..sheet.lastRowNum) {
            val row = sheet.getRow(rowIdx) ?: continue
            val missing = requiredColumns
                .filter { (col, _) -> getCellValue(row, col).isBlank() }
                .map { it.second }
            if (missing.isNotEmpty()) {
                validationErrors.add("${rowIdx + 1}행: ${missing.joinToString(", ")} 없음")
            }
        }

        // hawbNo 검증 (section:hawb = 1:1)
        val hawbWarnings = mutableListOf<String>()
        // 엑셀 row 수집: (sectionNo, hawbNo)
        data class SectionRow(val sectionNo: String, val hawbNo: String)
        val excelSectionRows = mutableListOf<SectionRow>()
        for (rowIdx in 1..sheet.lastRowNum) {
            val row = sheet.getRow(rowIdx) ?: continue
            val sectionNo = getCellValue(row, 4)
            val hawbNo = getCellValue(row, 3)
            if (sectionNo.isBlank()) continue
            excelSectionRows.add(SectionRow(sectionNo, hawbNo))
        }
        // (C) 파일 내 같은 hawb 가 여러 다른 section_no 에 연결
        val sectionsByHawb = excelSectionRows
            .filter { it.hawbNo.isNotBlank() }
            .groupBy({ it.hawbNo }, { it.sectionNo })
            .mapValues { it.value.toSet() }
        for ((hawb, sections) in sectionsByHawb) {
            if (sections.size > 1) {
                hawbWarnings.add(
                    "엑셀업로드 파일에 T송장: ${hawb} 이 주문섹션번호: ${sections.joinToString(", ")} 여러 개에 연결되어 있습니다. 확인해주세요"
                )
            }
        }
        // (C') 파일 내 같은 section_no 가 여러 row 에 존재 → 경고 + 전부 저장 제외
        val rowsBySection = excelSectionRows.groupBy { it.sectionNo }
        val duplicateSectionsInFile = rowsBySection.filter { it.value.size > 1 }.keys
        for (sectionNo in duplicateSectionsInFile) {
            val hawbs = rowsBySection[sectionNo]!!.map { it.hawbNo.ifBlank { "(빈값)" } }.distinct()
            hawbWarnings.add(
                "엑셀업로드 파일에 주문섹션번호: ${sectionNo} 이 여러 row 에 존재합니다 (T송장: ${hawbs.joinToString(", ")}). 파일을 확인 후 다시 업로드해주세요"
            )
        }
        // (D) DB 에 이미 있는 section_no 인데 hawbNo 가 다름
        for (row in excelSectionRows) {
            if (row.hawbNo.isBlank()) continue
            val dbRow = existingSectionMap[row.sectionNo] ?: continue
            val dbHawb = dbRow.hawbNo ?: ""
            if (dbHawb != row.hawbNo) {
                hawbWarnings.add(
                    "이미 등록된 주문섹션번호: ${row.sectionNo}, T송장: ${dbHawb} 이 " +
                        "엑셀업로드 파일에 주문섹션번호: ${row.sectionNo}, T송장: ${row.hawbNo} 이 다릅니다. 확인해주세요"
                )
            }
        }
        // (E) DB 에 이미 다른 section_no 로 hawb 존재
        val excelHawbSet = excelSectionRows.mapNotNull { it.hawbNo.takeIf { h -> h.isNotBlank() } }.toSet()
        val dbSectionsByHawb = if (excelHawbSet.isNotEmpty()) {
            imwebOrderSectionRepository.findByHawbNoIn(excelHawbSet).associateBy { it.hawbNo ?: "" }
        } else emptyMap()
        for (row in excelSectionRows) {
            if (row.hawbNo.isBlank()) continue
            val dbRow = dbSectionsByHawb[row.hawbNo] ?: continue
            val dbSectionNo = dbRow.imwebOrderSectionNo
            if (dbSectionNo != row.sectionNo) {
                hawbWarnings.add(
                    "이미 T송장: ${row.hawbNo} 이 주문섹션번호: ${dbSectionNo} 에 등록되어 있는데, " +
                        "엑셀업로드 파일에 주문섹션번호: ${row.sectionNo} 으로 있습니다. 확인해주세요"
                )
            }
        }

        val now = LocalDateTime.now()
        val toSave = mutableListOf<ImwebOrderSection>()
        for (rowIdx in 1..sheet.lastRowNum) {
            val row = sheet.getRow(rowIdx) ?: continue
            val sectionNo = getCellValue(row, 4)
            if (sectionNo.isBlank()) continue
            if (sectionNo in existingSectionNos) continue
            if (sectionNo in duplicateSectionsInFile) continue

            toSave.add(
                ImwebOrderSection(
                    imwebOrderSectionNo = sectionNo,                    // E: 주문섹션번호
                    imwebOrderNo = getCellValue(row, 0),                // A: 아임웹 주문번호
                                                                         // B: 주문자 이름 (미저장)
                                                                         // C: 주문자 번호 (미저장)
                    hawbNo = getCellValue(row, 3).ifBlank { null },     // D: 배송송장번호
                    createdId = userId,
                    createdAt = now,
                    updatedId = userId,
                    updatedAt = now
                )
            )
        }

        workbook.close()

        if (toSave.isNotEmpty()) {
            imwebOrderSectionRepository.saveAll(toSave)
            logger.info("아임웹 주문섹션 엑셀 업로드 완료: ${toSave.size}건 저장")
        }

        return ImwebOrderSectionUploadResult(
            totalCount = excelSectionNos.size,
            savedCount = toSave.size,
            skippedCount = existingSectionNos.size,
            validationErrors = validationErrors,
            hawbWarnings = hawbWarnings
        )
    }

    /** 헤더 문자열 정규화: 공백/탭/개행 등 모든 공백문자 제거 */
    private fun normalizeHeader(raw: String): String {
        return raw.replace(Regex("\\s+"), "")
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
}
