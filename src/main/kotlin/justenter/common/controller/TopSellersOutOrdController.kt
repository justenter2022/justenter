package justenter.common.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import justenter.cjdeliveryapi.dto.response.ApiResponse
import justenter.common.dto.BarcodeScanResult
import justenter.common.service.TopSellersOutOrdService
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.FileSystemResource
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.time.LocalDate

@Controller
class MainViewController {

    @GetMapping("/")
    fun mainPage(): String {
        return "main"
    }
}

@Controller
@RequestMapping("/topsellers")
class TopSellersOutOrdViewController {

    @GetMapping("/upload")
    fun uploadPage(): String {
        return "topsellers/upload"
    }

    @GetMapping("/scan")
    fun scanPage(): String {
        return "topsellers/scan"
    }

    @GetMapping("/label-print-view")
    fun labelPrintViewPage(): String {
        return "topsellers/label-print-view"
    }
}

@Tag(name = "탑셀러 출고 주문", description = "탑셀러 출고 주문 관리 API")
@RestController
@RequestMapping("/api/v1/topsellers")
class TopSellersOutOrdController(
    private val topSellersOutOrdService: TopSellersOutOrdService,
    @Value("\${app.invoice.base-path:./invoice}") private val invoiceBasePath: String
) {

    @Operation(summary = "엑셀 업로드", description = "탑셀러 출고 주문 엑셀 파일을 업로드하여 DB에 저장합니다.")
    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadExcel(
        @RequestParam("file") file: MultipartFile
    ): ApiResponse<Map<String, Any>> {
        if (file.isEmpty) {
            return ApiResponse(success = false, message = "파일이 비어있습니다.")
        }

        val originalFilename = file.originalFilename ?: ""
        if (!originalFilename.endsWith(".xlsx") && !originalFilename.endsWith(".xls")) {
            return ApiResponse(success = false, message = "엑셀 파일(.xlsx, .xls)만 업로드 가능합니다.")
        }

        val result = topSellersOutOrdService.uploadExcel(file)

        return ApiResponse(
            success = true,
            message = "엑셀 업로드 완료: ${result.savedCount}건 저장",
            data = mapOf(
                "totalCount" to result.totalCount,
                "savedCount" to result.savedCount,
                "skippedCount" to result.skippedCount,
                "validationErrors" to result.validationErrors,
                "hawbWarnings" to result.hawbWarnings,
                "fileName" to originalFilename,
                "todayBundleGroups" to result.todayBundleGroups,
                "todayBundleItems" to result.todayBundleItems,
                "remainingBundleGroups" to result.remainingBundleGroups,
                "remainingBundleItems" to result.remainingBundleItems
            )
        )
    }

    @Operation(summary = "바코드 스캔 처리", description = "바코드 스캔 값으로 주소정제 → 운송장번호 채번 → 라벨 생성 → 택배예약접수까지 일괄 처리합니다.")
    @PostMapping("/scan/{barcodeValue}")
    fun processBarcodeScan(
        @PathVariable barcodeValue: String
    ): ApiResponse<BarcodeScanResult> {
        val result = topSellersOutOrdService.processBarcodeScan(barcodeValue)

        return ApiResponse(
            success = result.success,
            message = result.message,
            data = result
        )
    }

    @Operation(summary = "아임웹 송장일괄등록 양식 다운로드", description = "지정된 날짜(기본 당일)에 송장 발급된 데이터를 아임웹 송장일괄등록 양식으로 다운로드합니다. brandId 가 없으면 브랜드별 xlsx 를 ZIP 으로 묶어 반환합니다.")
    @GetMapping("/imweb-invoice-download")
    fun downloadImwebInvoiceExcel(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate?,
        @RequestParam(required = false) brandId: Long?
    ): ResponseEntity<ByteArrayResource> {
        val targetDate = date ?: LocalDate.now()
        val excelResult = topSellersOutOrdService.generateImwebInvoiceExcel(targetDate, brandId)

        val encodedFileName = URLEncoder.encode(excelResult.fileName, StandardCharsets.UTF_8).replace("+", "%20")
        // 경고 메시지는 응답 헤더로 전달 (비 ASCII 이므로 URL 인코딩)
        val warningsHeader = excelResult.warnings.joinToString("\u001E") // RS 구분자
        val encodedWarnings = URLEncoder.encode(warningsHeader, StandardCharsets.UTF_8)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$encodedFileName\"; filename*=UTF-8''$encodedFileName")
            .header("X-Hawb-Warnings", encodedWarnings)
            .header("X-Row-Count", excelResult.rowCount.toString())
            .header("Access-Control-Expose-Headers", "X-Hawb-Warnings, X-Row-Count, Content-Disposition")
            .contentType(MediaType.parseMediaType(excelResult.contentType))
            .contentLength(excelResult.bytes.size.toLong())
            .body(ByteArrayResource(excelResult.bytes))
    }

    @Operation(summary = "브랜드 목록 조회", description = "아임웹 송장양식 다운로드용 브랜드 목록을 조회합니다.")
    @GetMapping("/brands")
    fun listBrands(): ApiResponse<List<Map<String, Any>>> {
        val brands = topSellersOutOrdService.listBrands()
            .map { mapOf<String, Any>("id" to it.id, "name" to it.name) }
        return ApiResponse(success = true, message = "브랜드 ${brands.size}건 조회", data = brands)
    }

    @Operation(summary = "금일마감", description = "송장 발급이 완료된 합포장 순번을 해제하여 다음 업로드에서 재사용 가능하게 합니다. 진행중/대기중 묶음의 번호는 유지됩니다.")
    @PostMapping("/close-today")
    fun closeToday(): ApiResponse<Map<String, Any>> {
        val released = topSellersOutOrdService.closeToday()
        return ApiResponse(
            success = true,
            message = if (released > 0) "금일마감 완료: ${released}개 합포장 순번 해제"
                      else "금일마감 완료: 해제할 합포장이 없습니다.",
            data = mapOf("releasedCount" to released)
        )
    }

    @Operation(summary = "완료 - 미출력 주문 피드백 엑셀 다운로드", description = "송장 미출력 주문을 엑셀 파일로 생성하여 다운로드합니다.")
    @PostMapping("/complete")
    fun complete(): ResponseEntity<FileSystemResource> {
        val filePath = topSellersOutOrdService.generateFeedbackExcel()
        val resource = FileSystemResource(filePath)

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${resource.filename}\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(resource)
    }

    @Operation(summary = "라벨 이미지 조회", description = "저장된 라벨 이미지를 조회합니다.")
    @GetMapping("/labels/{year}/{month}/{day}/{fileName}")
    fun getLabelImage(
        @PathVariable year: String,
        @PathVariable month: String,
        @PathVariable day: String,
        @PathVariable fileName: String
    ): ResponseEntity<FileSystemResource> {
        val filePath = Paths.get(invoiceBasePath, year, month, day, fileName)
        val resource = FileSystemResource(filePath)

        if (!resource.exists()) {
            return ResponseEntity.notFound().build()
        }

        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(resource)
    }
}
