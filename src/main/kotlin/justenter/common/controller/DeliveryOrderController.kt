package justenter.common.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import justenter.cjdeliveryapi.dto.response.ApiResponse
import justenter.common.service.DeliveryOrderService
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@Controller
@RequestMapping("/delivery")
class DeliveryOrderViewController {

    @GetMapping("/upload")
    fun uploadPage(): String {
        return "DeliveryOrderUpload"
    }
}

@Tag(name = "배송 주문", description = "엑셀 업로드를 통한 배송 주문 관리 API")
@RestController
@RequestMapping("/api/v1/delivery")
class DeliveryOrderController(
    private val deliveryOrderService: DeliveryOrderService
) {

    @Operation(summary = "엑셀 업로드", description = "엑셀 파일(.xlsx)을 업로드하여 배송 주문 데이터를 DB에 저장합니다.")
    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadExcel(
        @RequestParam("file") file: MultipartFile
    ): ApiResponse<Map<String, Any>> {
        if (file.isEmpty) {
            return ApiResponse(
                success = false,
                message = "파일이 비어있습니다."
            )
        }

        val originalFilename = file.originalFilename ?: ""
        if (!originalFilename.endsWith(".xlsx") && !originalFilename.endsWith(".xls")) {
            return ApiResponse(
                success = false,
                message = "엑셀 파일(.xlsx, .xls)만 업로드 가능합니다."
            )
        }

        val savedCount = deliveryOrderService.uploadExcel(file)

        return ApiResponse(
            success = true,
            message = "엑셀 업로드 완료: ${savedCount}건 저장",
            data = mapOf("savedCount" to savedCount, "fileName" to originalFilename)
        )
    }

    @Operation(summary = "배송 주문 목록 조회", description = "저장된 전체 배송 주문 목록을 조회합니다.")
    @GetMapping("/orders")
    fun getOrders(): ApiResponse<List<Map<String, Any?>>> {
        val orders = deliveryOrderService.findAll()
        val result = orders.map { order ->
            mapOf(
                "id" to order.id,
                "no" to order.no,
                "imwebOrderNo" to order.imwebOrderNo,
                "hawbNo" to order.hawbNo,
                "cjNo" to order.cjNo,
                "consigneeName" to order.consigneeName,
                "consigneeAddress" to order.consigneeAddress,
                "consigneeTel" to order.consigneeTel,
                "zipCode" to order.zipCode,
                "consigneeIdCardNo" to order.consigneeIdCardNo,
                "itemCode" to order.itemCode,
                "totalAmount" to order.totalAmount,
                "description" to order.description,
                "brand" to order.brand,
                "qty" to order.qty,
                "qtyUnit" to order.qtyUnit,
                "grossWeight" to order.grossWeight,
                "currency" to order.currency,
                "invoiceValue" to order.invoiceValue,
                "regTime" to order.regTime.toString()
            )
        }
        return ApiResponse(
            success = true,
            message = "조회 완료: ${result.size}건",
            data = result
        )
    }
}
