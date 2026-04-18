package justenter.cjdeliveryapi.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "배송 이력 상세")
data class TrackingDetail(
    @Schema(description = "진행 단계", example = "배송완료")
    @param:JsonProperty("status")
    val status: String,
    
    @Schema(description = "처리 일시", example = "2024-03-06 14:30:25")
    @param:JsonProperty("time")
    val time: String,
    
    @Schema(description = "위치", example = "서울 송파구 배송중")
    @param:JsonProperty("location")
    val location: String,
    
    @Schema(description = "상세 설명", example = "고객님이 상품을 수령하셨습니다.")
    @param:JsonProperty("description")
    val description: String
)
