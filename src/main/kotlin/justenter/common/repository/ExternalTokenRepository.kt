package justenter.common.repository

import justenter.common.entity.ExternalToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ExternalTokenRepository : JpaRepository<ExternalToken, String> {  // PK 타입이 String
    fun findByCompany(company: String): ExternalToken?
    fun findByToken(token: String): ExternalToken?
}
