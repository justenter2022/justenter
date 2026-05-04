package justenter.common.repository

import justenter.common.entity.ImwebOrderSection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ImwebOrderSectionRepository : JpaRepository<ImwebOrderSection, String> {
    fun findByImwebOrderSectionNoIn(sectionNos: Collection<String>): List<ImwebOrderSection>

    fun findByHawbNoIn(hawbNos: Collection<String>): List<ImwebOrderSection>

    fun findByImwebOrderNoInAndHawbNoIn(
        imwebOrderNos: Collection<String>,
        hawbNos: Collection<String>
    ): List<ImwebOrderSection>

    @Query("""
        SELECT s FROM ImwebOrderSection s
        WHERE s.imwebOrderNo IN :imwebOrderNos
          AND s.hawbNo IN :hawbNos
          AND s.invoiceNo IS NULL
    """)
    fun findUnInvoicedByImwebOrderNoInAndHawbNoIn(
        imwebOrderNos: Collection<String>,
        hawbNos: Collection<String>
    ): List<ImwebOrderSection>
}
