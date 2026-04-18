package justenter.cjdeliveryapi.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class InvoiceNumberResponse(
    @field:JsonProperty("RESULT_CD")
    val resultCd: String,

    @field:JsonProperty("RESULT_DETAIL")
    val resultDetail: String,

    @field:JsonProperty("DATA")
    val data: InvoiceNumberData? = null
)

data class InvoiceNumberData(
    @field:JsonProperty("INVC_NO")
    val invcNo: String
)
