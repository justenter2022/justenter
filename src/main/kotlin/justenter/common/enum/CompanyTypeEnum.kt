package justenter.common.enums

/**
 * 외부 API 연동 회사 구분
 */
enum class CompanyType(
    val code: String,
    val description: String
) {
    CJ("CJ", "CJ 대한통운"),
    HANJIN("HANJIN", "한진택배");

    companion object {
        /**
         * code로 CompanyType 찾기
         */
        fun fromCode(code: String): CompanyType? {
            return entries.find { it.code == code }
        }

        /**
         * code로 CompanyType 찾기 (없으면 예외)
         */
        fun fromCodeOrThrow(code: String): CompanyType {
            return fromCode(code) 
                ?: throw IllegalArgumentException("Unknown company code: $code")
        }
    }
}
