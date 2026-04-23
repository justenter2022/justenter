package justenter.common.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "top_sellers_out_ord")
class TopSellersOutOrd(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "no", length = 50)
    var no: String? = null,

    @Column(name = "imweb_order_no", length = 100)
    var imwebOrderNo: String? = null,

    @Column(name = "hawb_no", length = 100)
    var hawbNo: String? = null,

    @Column(name = "invoice_no", length = 100)
    var invoiceNo: String? = null,

    @Column(name = "invoice_issued_at")
    var invoiceIssuedAt: LocalDateTime? = null,

    @Column(name = "cust_nm", length = 255)
    var custNm: String? = null,

    @Column(name = "cust_address", columnDefinition = "TEXT")
    var custAddress: String? = null,

    @Column(name = "cust_tel_no", length = 50)
    var custTelNo: String? = null,

    @Column(name = "zip_code", length = 20)
    var zipCode: String? = null,

    @Column(name = "consignee_id_card_no", length = 50)
    var consigneeIdCardNo: String? = null,

    @Column(name = "allowed_item_code", length = 50)
    var allowedItemCode: String? = null,

    @Column(name = "price", precision = 18, scale = 2)
    var price: BigDecimal? = null,

    @Column(name = "product_type", length = 255)
    var productType: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    var brand: Brand? = null,

    @Column(name = "qty")
    var qty: Int? = null,

    @Column(name = "qty_unit", length = 20)
    var qtyUnit: String? = null,

    @Column(name = "bundle_group", length = 100)
    var bundleGroup: String? = null,

    @Column(name = "scanned", nullable = false)
    var scanned: Boolean = false,

    @Column(name = "bundle_seq")
    var bundleSeq: Int? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
