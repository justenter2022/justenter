package justenter.common.dto

data class ExcelUploadResult(
    val totalCount: Int,
    val savedCount: Int,
    val skippedCount: Int,
    val validationErrors: List<String>,
    val todayBundleGroups: Int,
    val todayBundleItems: Int,
    val remainingBundleGroups: Int,
    val remainingBundleItems: Int
)
