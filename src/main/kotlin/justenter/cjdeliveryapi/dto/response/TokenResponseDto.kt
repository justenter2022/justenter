package justenter.cjdeliveryapi.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class TokenResponse(
    @param:JsonProperty("RESULT_CD")
    val resultCd: String,
    
    @param:JsonProperty("RESULT_DETAIL")
    val resultDetail: String,
    
    @param:JsonProperty("DATA")
    val data: TokenData? = null
)

data class TokenData(
    @param:JsonProperty("TOKEN_NUM")
    val tokenNum: String,
    
    @param:JsonProperty("TOKEN_EXPRTN_DTM")
    val tokenExpireDtm: String,
    
    @param:JsonProperty("NOTICE")
    val notice: String? = null
)
