package justenter.cjdeliveryapi.dto.request

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

data class TokenRequest(
    @field:NotBlank(message = "고객 ID는 필수입니다")
    @get:JsonProperty("CUST_ID")
    val custId: String,
    
    @field:NotBlank(message = "사업자 번호는 필수입니다")
    @get:JsonProperty("BIZ_REG_NUM")
    val bizRegNum: String,

    @get:JsonProperty("USER_ID")
    val userId: String? = null
)
