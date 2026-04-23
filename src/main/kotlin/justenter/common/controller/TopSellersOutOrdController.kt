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
        return "Main"
    }
}

@Controller
@RequestMapping("/topsellers")
class TopSellersOutOrdViewController {

    @GetMapping("/upload")
    fun uploadPage(): String {
        return "TopSellersUpload"
    }

    @GetMapping("/scan")
    fun scanPage(): String {
        return "TopSellersScan"
    }

    @GetMapping("/label-print-view")
    fun labelPrintViewPage(): String {
        return "TopSellersLabelPrintView"
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

    @Operation(summary = "아임웹 송장일괄등록 양식 다운로드", description = "지정된 날짜(기본 당일)에 송장 발급된 데이터를 아임웹 송장일괄등록 양식으로 다운로드합니다.")
    @GetMapping("/imweb-invoice-download")
    fun downloadImwebInvoiceExcel(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate?
    ): ResponseEntity<ByteArrayResource> {
        val targetDate = date ?: LocalDate.now()
        val (fileName, bytes) = topSellersOutOrdService.generateImwebInvoiceExcel(targetDate)

        val encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20")
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$encodedFileName\"; filename*=UTF-8''$encodedFileName")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .contentLength(bytes.size.toLong())
            .body(ByteArrayResource(bytes))
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
