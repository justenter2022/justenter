package justenter.common.dto

data class ExcelUploadResult(
    val savedCount: Int,
    val todayBundleGroups: Int,
    val todayBundleItems: Int,
    val remainingBundleGroups: Int,
    val remainingBundleItems: Int
)
