package justenter.cjdeliveryapi.service

import justenter.cjdeliveryapi.client.CjApiClient
import justenter.cjdeliveryapi.dto.response.TrackingResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CjDeliveryService(
    private val cjApiClient: CjApiClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getTrackingInfo(invoiceNo: String): TrackingResponse {
        logger.info("배송 조회 요청 - 운송장번호: $invoiceNo")
        
        val response = cjApiClient.getTrackingInfo(invoiceNo)
        
        logger.info("배송 조회 완료 - 운송장번호: $invoiceNo, 상태: ${response.status}")
        
        return response
    }
}
