package justenter.cjdeliveryapi.service

import justenter.cjdeliveryapi.client.CjApiClient
import justenter.cjdeliveryapi.dto.response.TokenResponse
import justenter.common.entity.ExternalToken
import justenter.common.enums.CompanyType
import justenter.common.service.ExternalTokenService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * CJ 대한통운 API 토큰 관리 서비스
 * Common ExternalTokenService를 활용하여 토큰 관리
 */
@Service
class CjTokenService(
    private val cjApiClient: CjApiClient,
    private val externalTokenService: ExternalTokenService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val COMPANY = CompanyType.CJ
    }

    /**
     * 유효한 토큰을 가져오거나 자동으로 갱신
     * 1. DB에서 토큰 조회
     * 2. 토큰이 없거나 만료되었으면 새로 발행
     * 3. 유효한 토큰이면 반환
     */
    @Transactional
    fun getOrRefreshToken(): String {
        // 유효한 토큰 조회 (만료 시간 체크 포함)
        val validToken = getValidToken()

        return if (validToken != null) {
            logger.info("[${COMPANY.description}] 유효한 토큰 사용 - TOKEN: ${validToken.token}, 만료일시: ${validToken.tokenExpireDtm}")
            validToken.token
        } else {
            logger.info("[${COMPANY.description}] 유효한 토큰이 없습니다. 새로 발행합니다.")
            val response = issueOneDayToken()
            response.data!!.tokenNum
        }
    }

    /**
     * CJ API로부터 1Day 토큰 발행
     */
    @Transactional
    fun issueOneDayToken(): TokenResponse {
        logger.info("[${COMPANY.description}] 1Day 토큰 발행 서비스 시작")

        val response = cjApiClient.requestOneDayToken()

        if (response.resultCd == "S" && response.data != null) {
            logger.info("[${COMPANY.description}] 1Day 토큰 발행 성공 - TOKEN: ${response.data.tokenNum}")

            // 공통 서비스를 통해 토큰 저장
            val savedToken = externalTokenService.saveOrUpdate(
                company = COMPANY.code,
                token = response.data.tokenNum,
                tokenExpireDtm = response.data.tokenExpireDtm,
                regId = "SYSTEM"
            )
            logger.info("[${COMPANY.description}] 토큰 DB 저장/업데이트 완료 - COMPANY: ${savedToken.company}, 만료일시: ${savedToken.tokenExpireDtm}")
        } else {
            logger.warn("[${COMPANY.description}] 1Day 토큰 발행 실패 - RESULT_CD: ${response.resultCd}, DETAIL: ${response.resultDetail}")
        }

        return response
    }

    /**
     * 최신 CJ 토큰 조회 (유효성 체크 없음)
     */
    @Transactional(readOnly = true)
    fun getLatestToken(): ExternalToken? {
        return externalTokenService.findByCompany(COMPANY.code)
    }

    /**
     * 유효한 CJ 토큰 조회 (만료 시간 체크)
     * @return 유효한 토큰이 있으면 ExternalToken, 없거나 만료되었으면 null
     */
    @Transactional(readOnly = true)
    fun getValidToken(): ExternalToken? {
        val token = getLatestToken()

        if (token == null) {
            logger.info("[${COMPANY.description}] DB에 저장된 토큰이 없습니다.")
            return null
        }

        // 토큰 만료 시간 체크
        val expireDtm = try {
            LocalDateTime.parse(
                token.tokenExpireDtm,
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            )
        } catch (e: Exception) {
            logger.error("[${COMPANY.description}] 토큰 만료 시간 파싱 오류 - tokenExpireDtm: ${token.tokenExpireDtm}", e)
            return null
        }

        val now = LocalDateTime.now()

        return if (expireDtm.isAfter(now)) {
            logger.debug("[${COMPANY.description}] 유효한 토큰 존재 - 만료까지 남은 시간: ${java.time.Duration.between(now, expireDtm).toMinutes()}분")
            token
        } else {
            logger.warn("[${COMPANY.description}] 저장된 토큰이 만료되었습니다. - 만료일시: ${token.tokenExpireDtm}, 현재: ${now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))}")
            null
        }
    }
}