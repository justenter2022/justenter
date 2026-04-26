package justenter.common.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "imweb_order_section")
class ImwebOrderSection(
    @Id
    @Column(name = "imweb_order_section_no", length = 100)
    var imwebOrderSectionNo: String = "",

    @Column(name = "imweb_order_no", length = 100)
    var imwebOrderNo: String? = null,

    @Column(name = "hawb_no", length = 100)
    var hawbNo: String? = null,

    @Column(name = "created_id", length = 30)
    var createdId: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_id", length = 30)
    var updatedId: String? = null,

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
