package justenter.common.service

import justenter.common.repository.ExternalTokenRepository
import justenter.common.entity.ExternalToken
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 외부 API 토큰 관리 공통 서비스
 * 다양한 외부 API(CJ, 다른 택배사 등)의 토큰을 관리
 */
@Service
class ExternalTokenService(
    private val externalTokenRepository: ExternalTokenRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 회사별 토큰 조회
     * @param company 회사명 (예: "CJ", "HANJIN" 등)
     */
    @Transactional(readOnly = true)
    fun findByCompany(company: String): ExternalToken? {
        return externalTokenRepository.findByCompany(company)
    }

    /**
     * 토큰 값으로 조회
     */
    @Transactional(readOnly = true)
    fun findByToken(token: String): ExternalToken? {
        return externalTokenRepository.findByToken(token)
    }

    /**
     * 토큰 저장 또는 업데이트 (Upsert)
     * @param company 회사명
     * @param token 토큰 값
     * @param tokenExpireDtm 토큰 만료 일시 (yyyyMMddHHmmss)
     * @param notice 비고
     * @param regId 등록자 ID
     */
    @Transactional
    fun saveOrUpdate(
        company: String,
        token: String,
        tokenExpireDtm: String,
        regId: String = "SYSTEM"
    ): ExternalToken {
        val existingToken = externalTokenRepository.findByCompany(company)

        return if (existingToken != null) {
            // 기존 토큰 업데이트
            logger.info("[$company] 기존 토큰 업데이트 - TOKEN: $token")
            existingToken.token = token
            existingToken.tokenExpireDtm = tokenExpireDtm
            existingToken.updTime = LocalDateTime.now()
            externalTokenRepository.save(existingToken)
        } else {
            // 신규 토큰 생성
            logger.info("[$company] 새 토큰 생성 - TOKEN: $token")
            val newToken = ExternalToken(
                company = company,
                token = token,
                tokenExpireDtm = tokenExpireDtm,
                regId = regId
            )
            externalTokenRepository.save(newToken)
        }
    }

    /**
     * 토큰 삭제
     */
    @Transactional
    fun deleteByCompany(company: String) {
        externalTokenRepository.findByCompany(company)?.let {
            logger.info("[$company] 토큰 삭제")
            externalTokenRepository.delete(it)
        }
    }

    /**
     * 모든 토큰 조회
     */
    @Transactional(readOnly = true)
    fun findAll(): List<ExternalToken> {
        return externalTokenRepository.findAll()
    }
}
