package justenter.common.repository

import justenter.common.entity.TopSellersOutOrd
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface TopSellersOutOrdRepository : JpaRepository<TopSellersOutOrd, Long> {
    fun findByNo(no: String): TopSellersOutOrd?
    fun findByBundleGroup(bundleGroup: String): List<TopSellersOutOrd>

    @Query("SELECT COALESCE(MAX(t.bundleSeq), 0) FROM TopSellersOutOrd t WHERE t.scanned = true AND t.invoiceNo IS NULL")
    fun findMaxActiveBundleSeq(): Int
}
