
package justenter.common.service

import justenter.cjdeliveryapi.dto.response.AddressRefineData
import justenter.common.enum.Code128Type
import net.sourceforge.barbecue.Barcode
import net.sourceforge.barbecue.BarcodeFactory
import net.sourceforge.barbecue.BarcodeImageHandler
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.TextAttribute
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO

@Service
class ShippingLabelService {

    companion object {
        const val LABEL_WIDTH_PX = 480
        const val LABEL_HEIGHT_PX = 370
        const val RENDER_SCALE = 3
        // 출력용: 1152×888 = 4×3.08인치 at 288DPI (물리 크기 동일, 해상도 3배)
        const val OUTPUT_WIDTH_PX = 384 * RENDER_SCALE
        const val OUTPUT_HEIGHT_PX = 296 * RENDER_SCALE
        const val TARGET_DPI = 96 * RENDER_SCALE
    }

    /**
     * 운송장 이미지를 생성하여 ByteArray(PNG)로 반환합니다.
     * 템플릿 이미지 위에 동적 데이터를 오버레이합니다.
     */
    fun generateLabelImage(
        addressData: AddressRefineData,
        invoiceNo: String,
        sender: String,
        phoneNumber: String,
        address1: String,
        address2: String,
        shippingMemo: String,
        receiverName: String = "홍*동  010-1234-**** / 010-1234-****",
        receiverAddr1: String = "서울 중구 세종대로9길 53 [서소문동 58-12] 홍길동아파트 101동",
        receiverAddr2: String = "201호",
        productInfo: String = "테스트 TEST 상품 정보 ABCDEFG0000 컬러(COLOR) : 12345BK_블랙",
        productQty: String = "1"
    ): ByteArray {
        // 동적 데이터 추출
        val clsfcd = addressData.clsfcd ?: ""
        val subclsfcd = addressData.subclsfcd ?: ""
        val p2pcd = addressData.p2pcd ?: ""
        val clsfaddr = addressData.clsfaddr ?: ""
        val clldlvbrannm = addressData.clldlvbrannm ?: ""
        val clldlvempnicknm = addressData.clldlvempnicknm ?: ""

        // 오늘 날짜
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))

        val canvasWidth = LABEL_WIDTH_PX * RENDER_SCALE
        val canvasHeight = LABEL_HEIGHT_PX * RENDER_SCALE
        val labelImage = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB)
        val g2d: Graphics2D = labelImage.createGraphics()

        try {
            // 배경 초기화
            g2d.color = Color.WHITE
            g2d.fillRect(0, 0, canvasWidth, canvasHeight)

            // 품질 설정
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)

            // 2x 고해상도 렌더링 (좌표/폰트는 그대로, 픽셀만 2배)
            g2d.scale(RENDER_SCALE.toDouble(), RENDER_SCALE.toDouble())

            // 180도 회전
            g2d.rotate(Math.toRadians(180.0), (LABEL_WIDTH_PX / 2).toDouble(), (LABEL_HEIGHT_PX / 2).toDouble())

            val font9 = Font("SansSerif", Font.BOLD, 9)
            val font10 = Font("SansSerif", Font.BOLD, 10)
            val font11 = Font("SansSerif", Font.BOLD, 11)
            val font12 = Font("SansSerif", Font.BOLD, 12)
            val font14 = Font("SansSerif", Font.BOLD, 14)
            val font19 = Font("SansSerif", Font.BOLD, 19)
            val font23 = Font("SansSerif", Font.BOLD, 23)

            g2d.color = Color.BLACK

            // [1] 헤더 영역
            g2d.font = font14
            g2d.drawString(invoiceNo, 52, 20)
            g2d.font = font10
            g2d.drawString(today, 220, 18)
            g2d.drawString("1/1", 320, 18)

            // [2] 바코드 영역
            val clsfCdBarcode = generateBarcodeImage(clsfcd, 140, 55, Code128Type.CODE128A, false)
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g2d.drawImage(clsfCdBarcode, 0, 30, 140, 55, null)
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)

            val attributes = mapOf(
                TextAttribute.FAMILY to "SansSerif",
                TextAttribute.WEIGHT to TextAttribute.WEIGHT_BOLD,
                TextAttribute.SIZE to 46,
                TextAttribute.UNDERLINE to TextAttribute.UNDERLINE_ON
            )
            g2d.font = Font(attributes)
            g2d.drawString(clsfcd.substring(0, 1), 140, 86)

            g2d.font = Font("SansSerif", Font.BOLD, 58)
            g2d.drawString(clsfcd.substring(1), 167, 86)

            g2d.font = Font("SansSerif", Font.BOLD, 34)
            g2d.drawString("-$subclsfcd", 311, 82)
            g2d.font = Font("SansSerif", Font.BOLD, 28)
            g2d.drawString(p2pcd, 390, 82)

            // [3] 받는분 영역
            g2d.font = font12
            g2d.drawString(receiverName, 24, 110)
            g2d.drawString(receiverAddr1, 24, 128)
            g2d.drawString(receiverAddr2, 24, 140)
            val invoiceNoBarcode1 = generateBarcodeImage(invoiceNo, 155, 20, Code128Type.CODE128C, false)
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g2d.drawImage(invoiceNoBarcode1, 304, 93, 155, 20, null)
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)

            g2d.font = font23
            g2d.drawString(clsfaddr, 24, 162)

            // [4] 보내는분
            g2d.font = font9
            g2d.drawString(sender, 24, 176)
            g2d.drawString(phoneNumber, 164, 176)
            g2d.font = font10
            g2d.drawString("국소 1", 268, 176)
            g2d.drawString("0", 400, 176)
            g2d.drawString("신용", 450, 176)
            g2d.drawString(address1, 24, 190)

            // [5] 상품정보 영역
            g2d.font = font11
            g2d.drawString(productInfo, 14, 208)
            g2d.drawString(productQty, 460, 208)

            // [6] 배송메시지
            g2d.font = font10
            g2d.drawString(shippingMemo, 14, 330)
            g2d.font = font19
            g2d.drawString("$clldlvbrannm - $clldlvempnicknm", 40, 360)

            // [7] 하단 운송장바코드
            val invoiceNoBarcode2 = generateBarcodeImage(invoiceNo, 150, 33, Code128Type.CODE128C, true, 10)
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g2d.drawImage(invoiceNoBarcode2, 318, 316, 162, 33 + (10 + 5), null)
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)

        } finally {
            g2d.dispose()
        }

        // 480×370 → 384×296으로 리사이즈 (96DPI 기준 4인치 폭)
        val outputImage = resizeImage(labelImage, OUTPUT_WIDTH_PX, OUTPUT_HEIGHT_PX)
        val baos = ByteArrayOutputStream()
        writePngWithDpi(outputImage, baos, TARGET_DPI)
        return baos.toByteArray()
    }

    /**
     * PNG에 DPI 메타데이터(pHYs 청크)를 삽입하여 프린터가 올바른 물리 크기로 출력하도록 합니다.
     * 480px / 122DPI ≈ 100mm (4인치 라벨에 딱 맞는 크기)
     */
    private fun writePngWithDpi(image: BufferedImage, out: ByteArrayOutputStream, dpi: Int) {
        val writer = ImageIO.getImageWritersByFormatName("png").next()
        val writeParam = writer.defaultWriteParam
        val metadata = writer.getDefaultImageMetadata(
            javax.imageio.ImageTypeSpecifier.createFromBufferedImageType(image.type),
            writeParam
        )

        // PNG pHYs: pixels per meter
        val ppm = (dpi / 25.4 * 1000).toInt()
        val pHYs = javax.imageio.metadata.IIOMetadataNode("pHYs")
        pHYs.setAttribute("pixelsPerUnitXAxis", ppm.toString())
        pHYs.setAttribute("pixelsPerUnitYAxis", ppm.toString())
        pHYs.setAttribute("unitSpecifier", "meter")

        val root = metadata.getAsTree("javax_imageio_png_1.0") as javax.imageio.metadata.IIOMetadataNode
        root.appendChild(pHYs)
        metadata.setFromTree("javax_imageio_png_1.0", root)

        val ios = javax.imageio.stream.MemoryCacheImageOutputStream(out)
        writer.output = ios
        writer.write(javax.imageio.metadata.IIOMetadata::class.java.cast(null), javax.imageio.IIOImage(image, null, metadata), writeParam)
        ios.flush()
        writer.dispose()
    }

    /**
     * 템플릿 이미지를 로드하고 정확한 크기(123mm x 100mm)로 리사이즈합니다.
     */
    private fun loadAndResizeTemplate(): BufferedImage {
        val originalImage = try {
            val resource = ClassPathResource("templates/cj_shipping_label.png")
            ImageIO.read(resource.inputStream)
        } catch (e: Exception) {
            throw IllegalStateException("템플릿 이미지를 로드할 수 없습니다: ${e.message}", e)
        }

        // 이미 정확한 크기라면 그대로 반환
        if (originalImage.width == LABEL_WIDTH_PX && originalImage.height == LABEL_HEIGHT_PX) {
            return originalImage
        }

        // 정확한 크기로 리사이즈
        return resizeImage(originalImage, LABEL_WIDTH_PX, LABEL_HEIGHT_PX)
    }

    /**
     * 이미지를 지정된 크기로 리사이즈합니다.
     */
    private fun resizeImage(original: BufferedImage, targetWidth: Int, targetHeight: Int): BufferedImage {
        val resized = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val g2d = resized.createGraphics()

        try {
            // 고품질 리샘플링 설정
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null)
        } finally {
            g2d.dispose()
        }

        return resized
    }

    /**
     * CODE128 바코드 생성 (타입 지정 가능)
     * @param barcodeText 바코드 데이터
     * @param height 바코드 높이
     * @param type CODE128 타입 (A/B/C)
     * @return 바코드 이미지
     */
    private fun generateBarcodeImage(
        barcodeText: String,
        width: Int,
        height: Int,
        type: Code128Type,
        showText: Boolean = true,
        fontSize: Int = 15
    ): BufferedImage {
        val barcode: Barcode = when (type) {
            Code128Type.CODE128A -> BarcodeFactory.createCode128A(barcodeText)
            Code128Type.CODE128B -> BarcodeFactory.createCode128B(barcodeText)
            Code128Type.CODE128C -> BarcodeFactory.createCode128C(barcodeText)
        }

        // 바코드 바 비율을 유지하기 위해 리사이즈 없이 원본 크기로 생성
        barcode.setBarHeight(height)
        barcode.setBarWidth(2)
        barcode.setDrawingText(false)

        val barcodeImage = BarcodeImageHandler.getImage(barcode)

        if (!showText) {
            return barcodeImage
        }

        // 텍스트 공간을 포함한 새 이미지 생성
        val textHeight = fontSize + 5
        val totalHeight = barcodeImage.height + textHeight
        val combinedImage = BufferedImage(barcodeImage.width, totalHeight, BufferedImage.TYPE_INT_RGB)
        val g2d = combinedImage.createGraphics()

        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            // 배경 흰색으로 전체 채우기
            g2d.color = Color.WHITE
            g2d.fillRect(0, 0, combinedImage.width, combinedImage.height)
            // 바코드 이미지 그리기
            g2d.drawImage(barcodeImage, 0, 0, null)

            // 텍스트 중앙 정렬하여 그리기
            g2d.color = Color.BLACK
            g2d.font = Font("SansSerif", Font.PLAIN, fontSize)
            val fontMetrics = g2d.fontMetrics
            val textWidth = fontMetrics.stringWidth(barcodeText)
            val x = (barcodeImage.width - textWidth) / 2
            val y = barcodeImage.height + fontSize + 2
            g2d.drawString(barcodeText, x, y)

        } finally {
            g2d.dispose()
        }

        return combinedImage
    }
}
