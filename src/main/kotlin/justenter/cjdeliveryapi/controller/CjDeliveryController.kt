package justenter.cjdeliveryapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Pattern
import justenter.cjdeliveryapi.dto.response.AddressRefineData
import justenter.cjdeliveryapi.dto.response.AddressRefineResponse
import justenter.cjdeliveryapi.dto.response.ApiResponse
import justenter.cjdeliveryapi.dto.response.DeliveryBookingResponse
import justenter.cjdeliveryapi.dto.response.InvoiceNumberResponse
import justenter.cjdeliveryapi.dto.response.TokenResponse
import justenter.cjdeliveryapi.dto.response.TrackingResponse
import justenter.cjdeliveryapi.service.CjAddressService
import justenter.cjdeliveryapi.service.CjBookingService
import justenter.cjdeliveryapi.service.CjDeliveryService
import justenter.cjdeliveryapi.service.CjInvoiceService
import justenter.cjdeliveryapi.service.CjTokenService
import justenter.common.dto.GoodsItem
import justenter.common.service.ShippingLabelService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Controller
@RequestMapping("/cj")
@Validated
class CjDeliveryViewController {

    @GetMapping("/scan")
    fun scanPage(): String {
        return "BarcodeScanner"
    }

    @GetMapping("/labels/print")
    fun labelPrintPage(): String {
        return "LabelPrint"
    }

    @GetMapping("/labels/print-view")
    fun labelPrintViewPage(): String {
        return "LabelPrintView"
    }
}

@Tag(name = "CJ 대한통운", description = "CJ 대한통운 배송 조회 API")
@RestController
@RequestMapping("/api/v1/cj")
@Validated
class CjDeliveryController(
    private val cjAddressService: CjAddressService,
    private val cjBookingService: CjBookingService,
    private val cjDeliveryService: CjDeliveryService,
    private val cjInvoiceService: CjInvoiceService,
    private val cjTokenService: CjTokenService,
    private val shippingLabelService: ShippingLabelService
) {

    @Operation(summary = "주소정제", description = "입력한 주소를 정제합니다. (자동으로 토큰 갱신)")
    @PostMapping("/address/refine")
    fun refineAddress(
        @RequestParam address: String
    ): AddressRefineResponse {
        return cjAddressService.refineAddress(address)
    }

    @Operation(
        summary = "1Day 토큰 발행",
        description = "CJ 대한통운 API 사용을 위한 1일 유효 토큰을 발행합니다."
    )
    @PostMapping("/token")
    fun issueToken(): ApiResponse<TokenResponse> {
        val tokenResponse = cjTokenService.issueOneDayToken()

        return ApiResponse(
            success = tokenResponse.resultCd == "S",
            message = if (tokenResponse.resultCd == "S") "토큰 발행 성공" else tokenResponse.resultDetail,
            data = tokenResponse
        )
    }

    @Operation(
        summary = "운송장 번호로 배송 조회",
        description = "CJ 대한통운 운송장 번호로 배송 상태를 조회합니다."
    )
    @GetMapping("/tracking/{invoiceNo}")
    fun getTracking(
        @Parameter(description = "운송장 번호 (10~12자리)", example = "123456789012")
        @PathVariable
        @Pattern(regexp = "^[0-9]{10,12}$", message = "운송장 번호는 10~12자리 숫자여야 합니다.")
        invoiceNo: String
    ): ApiResponse<TrackingResponse> {
        val trackingInfo = cjDeliveryService.getTrackingInfo(invoiceNo)
        return ApiResponse(
            success = true,
            message = "배송 조회 성공",
            data = trackingInfo
        )
    }

    @Operation(summary = "운송장번호 채번", description = "CJ 대한통운 운송장 번호를 발급합니다. (자동으로 토큰 갱신)")
    @PostMapping("/invoice-number")
    fun issueInvoiceNumber(): ApiResponse<InvoiceNumberResponse> {
        val response = cjInvoiceService.issueInvoiceNumber()
        return ApiResponse(
            success = response.resultCd == "S",
            message = if (response.resultCd == "S") "운송장번호 채번 성공" else response.resultDetail,
            data = response
        )
    }

    @Operation(summary = "택배예약접수", description = "CJ 대한통운에 택배 예약을 접수합니다. (자동으로 토큰 갱신)")
    @PostMapping("/booking")
    fun registerBooking(
        @RequestParam custUseNo: String,
        @RequestParam invcNo: String,
        @RequestParam sendrNm: String,
        @RequestParam sendrTelNo1: String,
        @RequestParam sendrTelNo2: String,
        @RequestParam sendrTelNo3: String,
        @RequestParam(required = false, defaultValue = "") sendrZipNo: String,
        @RequestParam sendrAddr: String,
        @RequestParam(required = false, defaultValue = "") sendrDetailAddr: String,
        @RequestParam rcvrNm: String,
        @RequestParam rcvrTelNo1: String,
        @RequestParam rcvrTelNo2: String,
        @RequestParam rcvrTelNo3: String,
        @RequestParam(required = false, defaultValue = "") rcvrZipNo: String,
        @RequestParam rcvrAddr: String,
        @RequestParam(required = false, defaultValue = "") rcvrDetailAddr: String,
        @RequestParam gdsNm: String,
        @RequestParam(required = false, defaultValue = "") gdsQty: String,
        @RequestParam(required = false, defaultValue = "") gdsAmt: String
    ): ApiResponse<DeliveryBookingResponse> {
        val response = cjBookingService.registerBooking(
            custUseNo = custUseNo,
            invcNo = invcNo,
            sendrNm = sendrNm,
            sendrTelNo1 = sendrTelNo1,
            sendrTelNo2 = sendrTelNo2,
            sendrTelNo3 = sendrTelNo3,
            sendrZipNo = sendrZipNo,
            sendrAddr = sendrAddr,
            sendrDetailAddr = sendrDetailAddr,
            rcvrNm = rcvrNm,
            rcvrTelNo1 = rcvrTelNo1,
            rcvrTelNo2 = rcvrTelNo2,
            rcvrTelNo3 = rcvrTelNo3,
            rcvrZipNo = rcvrZipNo,
            rcvrAddr = rcvrAddr,
            rcvrDetailAddr = rcvrDetailAddr,
            goods = listOf(GoodsItem(productType = gdsNm, qty = gdsQty, amount = gdsAmt))
        )
        return ApiResponse(
            success = response.resultCd == "S",
            message = if (response.resultCd == "S") "택배예약접수 성공" else response.resultDetail,
            data = response
        )
    }

    @Operation(summary = "택배예약접수 요청 JSON 확인 (디버그용)")
    @GetMapping("/booking/debug")
    fun debugBookingJson(): Map<String, Any> {
        val goodsItem = justenter.cjdeliveryapi.dto.request.BookingGoodsItem(
            gdsNm = "상품",
            gdsQty = "1",
            gdsAmt = "10000"
        )
        val request = justenter.cjdeliveryapi.dto.request.DeliveryBookingRequest(
            tokenNum = "DEBUG_TOKEN",
            custId = "DEBUG_CUST",
            rcptYmd = "20260416",
            custUseNo = "TEST001",
            mpckKey = "20260416_DEBUG_CUST_TEST001",
            invcNo = "660029230891",
            sendrNm = "저스트엔터",
            sendrTelNo1 = "02",
            sendrTelNo2 = "1234",
            sendrTelNo3 = "1234",
            sendrZipNo = "08513",
            sendrAddr = "서울 금천구 디지털로 178",
            sendrDetailAddr = "B동 302호",
            rcvrNm = "안소정",
            rcvrTelNo1 = "010",
            rcvrTelNo2 = "4547",
            rcvrTelNo3 = "2001",
            rcvrZipNo = "05645",
            rcvrAddr = "서울특별시 송파구 백제고분로50길 32",
            rcvrDetailAddr = "404호",
            array = listOf(goodsItem)
        )
        return mapOf("DATA" to request)
    }

    @Operation(summary = "Health Check")
    @GetMapping("/health")
    fun healthCheck(): ApiResponse<Map<String, String>> {
        return ApiResponse(
            success = true,
            message = "CJ Delivery API is running",
            data = mapOf("status" to "UP", "version" to "v1.0.0")
        )
    }

    @GetMapping("/labels/preview", produces = [MediaType.IMAGE_PNG_VALUE])
    fun previewLabel(
        @RequestParam address: String,
        @RequestParam sender: String,
        @RequestParam phoneNumber: String,
        @RequestParam address1: String,
        @RequestParam(required = false, defaultValue = "") address2: String,
        @RequestParam(required = false, defaultValue = "") shippingMemo: String
    ): ResponseEntity<ByteArray> {
        // 주소정제
        val refineResponse = cjAddressService.refineAddress(address)
        val addressData = refineResponse.data
            ?: throw IllegalArgumentException("주소 정제 실패: ${refineResponse.resultDetail}")

        // 운송장번호 채번
        val invoiceResponse = cjInvoiceService.issueInvoiceNumber()
        val invoiceNo = invoiceResponse.data?.invcNo
            ?: throw IllegalArgumentException("운송장번호 채번 실패: ${invoiceResponse.resultDetail}")

        val imageBytes = shippingLabelService.generateLabelImage(
            addressData, invoiceNo, sender, phoneNumber, address1, address2, shippingMemo
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.IMAGE_PNG

        return ResponseEntity(imageBytes, headers, HttpStatus.OK)
    }
}
