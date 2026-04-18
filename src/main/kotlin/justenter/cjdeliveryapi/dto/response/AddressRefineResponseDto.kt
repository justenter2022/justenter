package justenter.cjdeliveryapi.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class AddressRefineResponse(
    @field:JsonProperty("RESULT_CD")
    val resultCd: String,

    @field:JsonProperty("RESULT_DETAIL")
    val resultDetail: String,

    @field:JsonProperty("DATA")
    val data: AddressRefineData? = null
)

data class AddressRefineData(
    @field:JsonProperty("CLSFCD")
    val clsfcd: String?,  // 분류코드 (예: "5D32")

    @field:JsonProperty("SUBCLSFCD")
    val subclsfcd: String?,  // 하위분류코드 (예: "1g")

    @field:JsonProperty("CLSFADDR")
    val clsfaddr: String?,  // 분류주소 (예: "서소문 58-12 대한통운")

    @field:JsonProperty("CLLDLVBRANNM")
    val clldlvbrannm: String?,  // 배달지점명 (예: "*송종훈")

    @field:JsonProperty("CLLDLVEMPNM")
    val clldlvempnm: String?,  // 배달사원명 (예: "##")

    @field:JsonProperty("CLLDLVEMPNICKNM")
    val clldlvempnicknm: String?,  // 배달사원별명 (예: "G03-1구역")

    @field:JsonProperty("RSPSDIV")
    val rspsdiv: String?,  // 응답구분 (예: "01")

    @field:JsonProperty("P2PCD")
    val p2pcd: String?  // P2P코드 (nullable)
)