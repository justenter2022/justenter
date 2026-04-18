package justenter.common.dto

data class BarcodeScanResult(
    val success: Boolean,
    val errorType: BarcodeScanErrorType? = null,
    val message: String,
    val invoiceNo: String? = null,
    val labelImagePath: String? = null,
    val bundleGroup: String? = null,
    val bundleTotal: Int = 0,
    val bundleScanned: Int = 0,
    val bundleComplete: Boolean = false,
    val bundleSeq: Int? = null
)

enum class BarcodeScanErrorType {
    NOT_FOUND,
    ADDRESS_REFINE_FAILED,
    INVOICE_FAILED,
    LABEL_GENERATION_FAILED,
    BOOKING_FAILED
}
