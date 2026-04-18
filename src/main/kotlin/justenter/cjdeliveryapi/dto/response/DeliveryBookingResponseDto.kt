package justenter.cjdeliveryapi.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class DeliveryBookingResponse(
    @field:JsonProperty("RESULT_CD")
    val resultCd: String,

    @field:JsonProperty("RESULT_DETAIL")
    val resultDetail: String
)
