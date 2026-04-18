package justenter.cjdeliveryapi.service

import justenter.cjdeliveryapi.client.CjApiClient
import justenter.cjdeliveryapi.dto.response.AddressRefineResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CjAddressService(
    private val cjApiClient: CjApiClient,
    private val cjTokenService: CjTokenService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun refineAddress(address: String): AddressRefineResponse {
        logger.info("주소정제 서비스 시작 - ADDRESS: $address")

        // 토큰 자동 갱신 (당일이면 재사용, 이전이면 새로 발행)
        val tokenNum = cjTokenService.getOrRefreshToken()

        // 주소정제 API 호출
        val response = cjApiClient.requestAddressRefine(tokenNum, address)

        if (response.resultCd == "S") {
            logger.info("주소정제 성공 - 정제된 주소: ${response.data?.clsfaddr}")
        } else {
            logger.warn("주소정제 실패 - RESULT_CD: ${response.resultCd}, DETAIL: ${response.resultDetail}")
        }

        return response
    }
}
