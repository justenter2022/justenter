package justenter.common.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import justenter.cjdeliveryapi.dto.response.ApiResponse
import justenter.common.service.ImwebOrderSectionService
import justenter.common.service.InvalidUploadFormatException
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@Controller
@RequestMapping("/imweb-order-section")
class ImwebOrderSectionViewController {

    @GetMapping("/upload")
    fun uploadPage(): String {
        return "imweb/order-section-upload"
    }
}

@Tag(name = "아임웹 주문섹션", description = "아임웹 주문섹션 관리 API")
@RestController
@RequestMapping("/api/v1/imweb-order-section")
class ImwebOrderSectionController(
    private val imwebOrderSectionService: ImwebOrderSectionService
) {

    @Operation(summary = "아임웹 주문섹션 엑셀 업로드", description = "아임웹 주문섹션 엑셀 파일을 업로드하여 DB에 저장합니다.")
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

        val result = try {
            imwebOrderSectionService.uploadExcel(file)
        } catch (e: InvalidUploadFormatException) {
            return ApiResponse(success = false, message = e.message ?: "업로드양식을 확인해주세요.")
        }

        return ApiResponse(
            success = true,
            message = "엑셀 업로드 완료: ${result.savedCount}건 저장",
            data = mapOf(
                "totalCount" to result.totalCount,
                "savedCount" to result.savedCount,
                "skippedCount" to result.skippedCount,
                "validationErrors" to result.validationErrors,
                "hawbWarnings" to result.hawbWarnings,
                "fileName" to originalFilename
            )
        )
    }
}
