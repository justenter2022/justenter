package justenter.common.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "delivery_order")
class DeliveryOrder(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "no", length = 50)
    var no: String? = null,

    @Column(name = "imweb_order_no", length = 50)
    var imwebOrderNo: String? = null,

    @Column(name = "hawb_no", length = 50)
    var hawbNo: String? = null,

    @Column(name = "cj_no", length = 50)
    var cjNo: String? = null,

    @Column(name = "consignee_name", length = 100)
    var consigneeName: String? = null,

    @Column(name = "consignee_address", length = 500)
    var consigneeAddress: String? = null,

    @Column(name = "consignee_tel", length = 30)
    var consigneeTel: String? = null,

    @Column(name = "zip_code", length = 10)
    var zipCode: String? = null,

    @Column(name = "consignee_id_card_no", length = 50)
    var consigneeIdCardNo: String? = null,

    @Column(name = "item_code", length = 20)
    var itemCode: String? = null,

    @Column(name = "total_amount", length = 20)
    var totalAmount: String? = null,

    @Column(name = "description", length = 200)
    var description: String? = null,

    @Column(name = "brand", length = 100)
    var brand: String? = null,

    @Column(name = "qty", length = 10)
    var qty: String? = null,

    @Column(name = "qty_unit", length = 10)
    var qtyUnit: String? = null,

    @Column(name = "gross_weight", length = 20)
    var grossWeight: String? = null,

    @Column(name = "currency", length = 10)
    var currency: String? = null,

    @Column(name = "invoice_value", length = 20)
    var invoiceValue: String? = null,

    @Column(name = "reg_time", nullable = false, updatable = false)
    val regTime: LocalDateTime = LocalDateTime.now()
)
