package justenter.cjdeliveryapi.dto.request

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * CJ 택배예약접수 요청 DTO
 * API: /RegBook (POST)
 * 구조: {"DATA": { ...fields..., "ARRAY": [{...goods...}] }}
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
data class DeliveryBookingRequest(
    // ==================== 필수/PK 필드 ====================

    @get:JsonProperty("TOKEN_NUM")
    val tokenNum: String,

    @get:JsonProperty("CUST_ID")
    val custId: String,

    @get:JsonProperty("RCPT_YMD")
    val rcptYmd: String,

    @get:JsonProperty("CUST_USE_NO")
    val custUseNo: String,

    // ==================== 접수/작업 구분 ====================

    @get:JsonProperty("RCPT_DV")
    val rcptDv: String = "01",

    @get:JsonProperty("WORK_DV_CD")
    val workDvCd: String = "01",

    @get:JsonProperty("REQ_DV_CD")
    val reqDvCd: String = "01",

    // ==================== 합포장/정산/운임 ====================

    @get:JsonProperty("MPCK_KEY")
    val mpckKey: String = "",

    @get:JsonProperty("CAL_DV_CD")
    val calDvCd: String = "01",

    @get:JsonProperty("FRT_DV_CD")
    val frtDvCd: String = "03",

    @get:JsonProperty("CNTR_ITEM_CD")
    val cntrItemCd: String = "01",

    // ==================== 박스 ====================

    @get:JsonProperty("BOX_TYPE_CD")
    val boxTypeCd: String = "01",

    @get:JsonProperty("BOX_QTY")
    val boxQty: String = "1",

    // ==================== 운임/고객관리 ====================

    @get:JsonProperty("FRT")
    val frt: String = "",

    @get:JsonProperty("CUST_MGMT_DLCM_CD")
    val custMgmtDlcmCd: String = "",

    // ==================== 보내는분 ====================

    @get:JsonProperty("SENDR_NM")
    val sendrNm: String = "",

    @get:JsonProperty("SENDR_TEL_NO1")
    val sendrTelNo1: String = "",

    @get:JsonProperty("SENDR_TEL_NO2")
    val sendrTelNo2: String = "",

    @get:JsonProperty("SENDR_TEL_NO3")
    val sendrTelNo3: String = "",

    @get:JsonProperty("SENDR_CELL_NO1")
    val sendrCellNo1: String = "",

    @get:JsonProperty("SENDR_CELL_NO2")
    val sendrCellNo2: String = "",

    @get:JsonProperty("SENDR_CELL_NO3")
    val sendrCellNo3: String = "",

    @get:JsonProperty("SENDR_SAFE_NO1")
    val sendrSafeNo1: String = "",

    @get:JsonProperty("SENDR_SAFE_NO2")
    val sendrSafeNo2: String = "",

    @get:JsonProperty("SENDR_SAFE_NO3")
    val sendrSafeNo3: String = "",

    @get:JsonProperty("SENDR_ZIP_NO")
    val sendrZipNo: String = "",

    @get:JsonProperty("SENDR_ADDR")
    val sendrAddr: String = "",

    @get:JsonProperty("SENDR_DETAIL_ADDR")
    val sendrDetailAddr: String = "",

    // ==================== 받는분 ====================

    @get:JsonProperty("RCVR_NM")
    val rcvrNm: String = "",

    @get:JsonProperty("RCVR_TEL_NO1")
    val rcvrTelNo1: String = "",

    @get:JsonProperty("RCVR_TEL_NO2")
    val rcvrTelNo2: String = "",

    @get:JsonProperty("RCVR_TEL_NO3")
    val rcvrTelNo3: String = "",

    @get:JsonProperty("RCVR_CELL_NO1")
    val rcvrCellNo1: String = "",

    @get:JsonProperty("RCVR_CELL_NO2")
    val rcvrCellNo2: String = "",

    @get:JsonProperty("RCVR_CELL_NO3")
    val rcvrCellNo3: String = "",

    @get:JsonProperty("RCVR_SAFE_NO1")
    val rcvrSafeNo1: String = "",

    @get:JsonProperty("RCVR_SAFE_NO2")
    val rcvrSafeNo2: String = "",

    @get:JsonProperty("RCVR_SAFE_NO3")
    val rcvrSafeNo3: String = "",

    @get:JsonProperty("RCVR_ZIP_NO")
    val rcvrZipNo: String = "",

    @get:JsonProperty("RCVR_ADDR")
    val rcvrAddr: String = "",

    @get:JsonProperty("RCVR_DETAIL_ADDR")
    val rcvrDetailAddr: String = "",

    // ==================== 주문자 ====================

    @get:JsonProperty("ORDRR_NM")
    val ordrrNm: String = "",

    @get:JsonProperty("ORDRR_TEL_NO1")
    val ordrrTelNo1: String = "",

    @get:JsonProperty("ORDRR_TEL_NO2")
    val ordrrTelNo2: String = "",

    @get:JsonProperty("ORDRR_TEL_NO3")
    val ordrrTelNo3: String = "",

    @get:JsonProperty("ORDRR_CELL_NO1")
    val ordrrCellNo1: String = "",

    @get:JsonProperty("ORDRR_CELL_NO2")
    val ordrrCellNo2: String = "",

    @get:JsonProperty("ORDRR_CELL_NO3")
    val ordrrCellNo3: String = "",

    @get:JsonProperty("ORDRR_SAFE_NO1")
    val ordrrSafeNo1: String = "",

    @get:JsonProperty("ORDRR_SAFE_NO2")
    val ordrrSafeNo2: String = "",

    @get:JsonProperty("ORDRR_SAFE_NO3")
    val ordrrSafeNo3: String = "",

    @get:JsonProperty("ORDRR_ZIP_NO")
    val ordrrZipNo: String = "",

    @get:JsonProperty("ORDRR_ADDR")
    val ordrrAddr: String = "",

    @get:JsonProperty("ORDRR_DETAIL_ADDR")
    val ordrrDetailAddr: String = "",

    // ==================== 운송장 ====================

    @get:JsonProperty("INVC_NO")
    val invcNo: String = "",

    @get:JsonProperty("ORI_INVC_NO")
    val oriInvcNo: String = "",

    @get:JsonProperty("ORI_ORD_NO")
    val oriOrdNo: String = "",

    // ==================== 집화/출고 ====================

    @get:JsonProperty("COLCT_EXPCT_YMD")
    val colctExpctYmd: String = "",

    @get:JsonProperty("COLCT_EXPCT_HOUR")
    val colctExpctHour: String = "",

    @get:JsonProperty("SHIP_EXPCT_YMD")
    val shipExpctYmd: String = "",

    @get:JsonProperty("SHIP_EXPCT_HOUR")
    val shipExpctHour: String = "",

    // ==================== 출력/금액/비고 ====================

    @get:JsonProperty("PRT_ST")
    val prtSt: String = "02",

    @get:JsonProperty("ARTICLE_AMT")
    val articleAmt: String = "",

    @get:JsonProperty("REMARK_1")
    val remark1: String = "",

    @get:JsonProperty("REMARK_2")
    val remark2: String = "",

    @get:JsonProperty("REMARK_3")
    val remark3: String = "",

    // ==================== COD/기타 ====================

    @get:JsonProperty("COD_YN")
    val codYn: String = "N",

    @get:JsonProperty("ETC_1")
    val etc1: String = "",

    @get:JsonProperty("ETC_2")
    val etc2: String = "",

    @get:JsonProperty("ETC_3")
    val etc3: String = "",

    @get:JsonProperty("ETC_4")
    val etc4: String = "",

    @get:JsonProperty("ETC_5")
    val etc5: String = "",

    // ==================== 배송/접수 ====================

    @get:JsonProperty("DLV_DV")
    val dlvDv: String = "01",

    @get:JsonProperty("RCPT_SERIAL")
    val rcptSerial: String = "",

    // ==================== 상품 배열 ====================

    @get:JsonProperty("ARRAY")
    val array: List<BookingGoodsItem> = emptyList()
)

/**
 * 택배예약접수 상품 상세 항목
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
data class BookingGoodsItem(
    @get:JsonProperty("MPCK_SEQ")
    val mpckSeq: String = "1",

    @get:JsonProperty("GDS_CD")
    val gdsCd: String = "",

    @get:JsonProperty("GDS_NM")
    val gdsNm: String = "",

    @get:JsonProperty("GDS_QTY")
    val gdsQty: String = "",

    @get:JsonProperty("UNIT_CD")
    val unitCd: String = "",

    @get:JsonProperty("UNIT_NM")
    val unitNm: String = "",

    @get:JsonProperty("GDS_AMT")
    val gdsAmt: String = ""
)
