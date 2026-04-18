package justenter.common.repository

import justenter.common.entity.DeliveryOrder
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DeliveryOrderRepository : JpaRepository<DeliveryOrder, Long> {
    fun findByHawbNo(hawbNo: String): DeliveryOrder?
    fun findByCjNo(cjNo: String): DeliveryOrder?
}
