package justenter.cjdeliveryapi.service

import justenter.cjdeliveryapi.client.CjApiClient
import justenter.cjdeliveryapi.dto.response.InvoiceNumberResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CjInvoiceService(
    private val cjApiClient: CjApiClient,
    private val cjTokenService: CjTokenService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun issueInvoiceNumber(): InvoiceNumberResponse {
        logger.info("운송장번호 채번 서비스 시작")

        val tokenNum = cjTokenService.getOrRefreshToken()

        val response = cjApiClient.requestInvoiceNumber(tokenNum)

        if (response.resultCd == "S") {
            logger.info("운송장번호 채번 성공 - INVC_NO: ${response.data?.invcNo}")
        } else {
            logger.warn("운송장번호 채번 실패 - RESULT_CD: ${response.resultCd}, DETAIL: ${response.resultDetail}")
        }

        return response
    }
}
