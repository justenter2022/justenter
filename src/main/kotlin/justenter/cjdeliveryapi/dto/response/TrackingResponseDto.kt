package justenter.cjdeliveryapi.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "배송 추적 응답")
data class TrackingResponse(
    @Schema(description = "택배사명", example = "CJ대한통운")
    @param:JsonProperty("carrier")
    val carrier: String = "CJ대한통운",
    
    @Schema(description = "운송장 번호", example = "123456789012")
    @param:JsonProperty("invoiceNo")
    val invoiceNo: String,
    
    @Schema(description = "현재 배송 상태", example = "배송완료")
    @param:JsonProperty("status")
    val status: String,
    
    @Schema(description = "보내는 분", example = "홍길동")
    @param:JsonProperty("senderName")
    val senderName: String? = null,
    
    @Schema(description = "받는 분", example = "김철수")
    @param:JsonProperty("receiverName")
    val receiverName: String? = null,
    
    @Schema(description = "배송 시작 주소", example = "서울특별시 강남구")
    @param:JsonProperty("from")
    val from: String? = null,
    
    @Schema(description = "배송 목적지 주소", example = "서울특별시 송파구")
    @param:JsonProperty("to")
    val to: String? = null,
    
    @Schema(description = "현재 위치", example = "서울 송파구 배송중")
    @param:JsonProperty("currentLocation")
    val currentLocation: String? = null,
    
    @Schema(description = "배송 완료 일시", example = "2024-03-06 14:30:25")
    @param:JsonProperty("deliveredAt")
    val deliveredAt: String? = null,
    
    @Schema(description = "상세 배송 이력")
    @param:JsonProperty("trackingDetails")
    val trackingDetails: List<TrackingDetail>? = null
)
