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
    fun findByHawbNoIn(hawbNos: Collection<String>): List<TopSellersOutOrd>

    // 송장 발급 완료된(invoiceNo 있는) 건은 제외하여, 현재 스캔/처리 중인 묶음 멤버만 반환
    @Query("SELECT t FROM TopSellersOutOrd t WHERE t.bundleGroup = :bundleGroup AND t.invoiceNo IS NULL")
    fun findActiveByBundleGroup(bundleGroup: String): List<TopSellersOutOrd>

    @Query("SELECT DISTINCT t.bundleGroup FROM TopSellersOutOrd t WHERE t.bundleGroup IS NOT NULL")
    fun findAllDistinctBundleGroups(): List<String>

    @Query("SELECT DISTINCT t.bundleSeq FROM TopSellersOutOrd t WHERE t.bundleSeq IS NOT NULL")
    fun findAllUsedBundleSeqs(): List<Int>

    @Query("SELECT t FROM TopSellersOutOrd t WHERE t.invoiceNo IS NOT NULL AND t.bundleSeq IS NOT NULL")
    fun findCompletedBundleOrdersWithSeq(): List<TopSellersOutOrd>

    fun findByInvoiceNoIsNull(): List<TopSellersOutOrd>

    @Query("SELECT t FROM TopSellersOutOrd t WHERE t.invoiceNo IS NOT NULL AND t.invoiceIssuedAt BETWEEN :start AND :end ORDER BY t.invoiceIssuedAt ASC, t.id ASC")
    fun findIssuedBetween(start: LocalDateTime, end: LocalDateTime): List<TopSellersOutOrd>

    @Query("SELECT t FROM TopSellersOutOrd t WHERE t.invoiceNo IS NOT NULL AND t.invoiceIssuedAt BETWEEN :start AND :end AND t.brand.id = :brandId ORDER BY t.invoiceIssuedAt ASC, t.id ASC")
    fun findIssuedBetweenByBrand(start: LocalDateTime, end: LocalDateTime, brandId: Long): List<TopSellersOutOrd>
}
