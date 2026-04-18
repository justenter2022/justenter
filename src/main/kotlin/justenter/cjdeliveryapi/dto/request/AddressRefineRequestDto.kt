package justenter.cjdeliveryapi.dto.request

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

data class AddressRefineRequest(
    @field:NotBlank(message = "토큰번호는 필수입니다")
    @get:JsonProperty("TOKEN_NUM")
    val tokenNum: String,

    @field:NotBlank(message = "고객번호는 필수입니다")
    @get:JsonProperty("CLNTNUM")
    val clntNum: String,

    @field:NotBlank(message = "주소는 필수입니다")
    @get:JsonProperty("ADDRESS")
    val address: String
)