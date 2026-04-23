package justenter.common.repository

import justenter.common.entity.TopSellersOutOrd
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface TopSellersOutOrdRepository : JpaRepository<TopSellersOutOrd, Long> {
    fun findByNo(no: String): TopSellersOutOrd?
    fun findByNoIn(nos: Collection<String>): List<TopSellersOutOrd>
    fun findByBundleGroup(bundleGroup: String): List<TopSellersOutOrd>

    @Query("SELECT COALESCE(MAX(t.bundleSeq), 0) FROM TopSellersOutOrd t WHERE t.scanned = true AND t.invoiceNo IS NULL")
    fun findMaxActiveBundleSeq(): Int

    fun findByInvoiceNoIsNull(): List<TopSellersOutOrd>

    @Query("SELECT t FROM TopSellersOutOrd t WHERE t.invoiceNo IS NOT NULL AND t.invoiceIssuedAt BETWEEN :start AND :end ORDER BY t.invoiceIssuedAt ASC, t.id ASC")
    fun findIssuedBetween(start: LocalDateTime, end: LocalDateTime): List<TopSellersOutOrd>
}
