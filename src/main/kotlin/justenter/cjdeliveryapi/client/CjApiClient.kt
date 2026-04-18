package justenter.cjdeliveryapi.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import justenter.cjdeliveryapi.dto.request.AddressRefineRequest
import justenter.cjdeliveryapi.dto.request.DeliveryBookingRequest
import justenter.cjdeliveryapi.dto.request.InvoiceNumberRequest
import justenter.cjdeliveryapi.dto.request.TokenRequest
import justenter.cjdeliveryapi.dto.response.AddressRefineResponse
import justenter.cjdeliveryapi.dto.response.DeliveryBookingResponse
import justenter.cjdeliveryapi.dto.response.InvoiceNumberResponse
import justenter.cjdeliveryapi.dto.response.TokenResponse
import justenter.cjdeliveryapi.dto.response.TrackingDetail
import justenter.cjdeliveryapi.dto.response.TrackingResponse
import justenter.config.CjDeliveryConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class CjApiClient(
    private val cjWebClient: WebClient,
    private val cjConfig: CjDeliveryConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    // ==================== 토큰 관련 ====================
    
    fun requestOneDayToken(): TokenResponse {
        return try {
            val request = TokenRequest(
                custId = cjConfig.custId,
                bizRegNum = cjConfig.bizRegNum
            )

            val uri = "/ReqOneDayToken"
            val requestBody = mapOf("DATA" to request)
            val requestBodyJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(requestBody)

            logger.info("""
                ========================================
                CJ 1Day 토큰 발행 요청
                ========================================
                URL: ${cjConfig.baseUrl}$uri
                Method: POST
                Request Body:
                $requestBodyJson
                ========================================
            """.trimIndent())

            val response = cjWebClient.post()
                .uri(uri)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono<TokenResponse>()
                .block()
                ?: throw IllegalStateException("토큰 발행 응답이 없습니다.")

            logger.info("CJ 1Day 토큰 발행 성공 - RESULT_CD: ${response.resultCd}")
            response

        } catch (e: Exception) {
            logger.error("CJ 1Day 토큰 발행 실패: ${e.message}", e)
            throw IllegalStateException("토큰 발행에 실패했습니다: ${e.message}", e)
        }
    }

    // ==================== 주소정제 관련 ====================

    fun requestAddressRefine(tokenNum: String, address: String): AddressRefineResponse {
        return try {
            val request = AddressRefineRequest(
                tokenNum = tokenNum,
                clntNum = cjConfig.custId,
                address = address
            )

            val uri = "/ReqAddrRfnSm"
            val requestBody = mapOf("DATA" to request)
            val requestBodyJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(requestBody)

            logger.info("""
                ========================================
                CJ 주소정제 API 요청
                ========================================
                URL: ${cjConfig.baseUrl}$uri
                Method: POST
                Headers:
                  CJ-Gateway-APIKey: ${tokenNum}
                Request Body:
                $requestBodyJson
                ========================================
            """.trimIndent())

            val response = cjWebClient.post()
                .uri(uri)
                .header("CJ-Gateway-APIKey", tokenNum)  // ← 발급받은 토큰
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono<AddressRefineResponse>()
                .block()
                ?: throw IllegalStateException("주소정제 응답이 없습니다.")

            val responseJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(response)

            logger.info("""
                ========================================
                CJ 주소정제 API 응답
                ========================================
                RESULT_CD: ${response.resultCd}
                Response Body:
                $responseJson
                ========================================
            """.trimIndent())

            response

        } catch (e: Exception) {
            logger.error("CJ 주소정제 실패: ${e.message}", e)
            throw IllegalStateException("주소정제에 실패했습니다: ${e.message}", e)
        }
    }

    // ==================== 운송장번호 채번 관련 ====================

    fun requestInvoiceNumber(tokenNum: String): InvoiceNumberResponse {
        return try {
            val request = InvoiceNumberRequest(
                tokenNum = tokenNum,
                clntNum = cjConfig.custId
            )

            val uri = "/ReqInvcNo"
            val requestBody = mapOf("DATA" to request)
            val requestBodyJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(requestBody)

            logger.info("""
                ========================================
                CJ 운송장번호 채번 API 요청
                ========================================
                URL: ${cjConfig.baseUrl}$uri
                Method: POST
                Headers:
                  CJ-Gateway-APIKey: $tokenNum
                Request Body:
                $requestBodyJson
                ========================================
            """.trimIndent())

            val response = cjWebClient.post()
                .uri(uri)
                .header("CJ-Gateway-APIKey", tokenNum)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono<InvoiceNumberResponse>()
                .block()
                ?: throw IllegalStateException("운송장번호 채번 응답이 없습니다.")

            val responseJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(response)

            logger.info("""
                ========================================
                CJ 운송장번호 채번 API 응답
                ========================================
                RESULT_CD: ${response.resultCd}
                Response Body:
                $responseJson
                ========================================
            """.trimIndent())

            response

        } catch (e: Exception) {
            logger.error("CJ 운송장번호 채번 실패: ${e.message}", e)
            throw IllegalStateException("운송장번호 채번에 실패했습니다: ${e.message}", e)
        }
    }

    // ==================== 택배예약접수 관련 ====================

    fun requestBooking(tokenNum: String, request: DeliveryBookingRequest): DeliveryBookingResponse {
        return try {
            val uri = "/RegBook"
            val requestBody = mapOf("DATA" to request)
            val requestBodyJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(requestBody)

            logger.info("""
                ========================================
                CJ 택배예약접수 API 요청
                ========================================
                URL: ${cjConfig.baseUrl}$uri
                Method: POST
                Headers:
                  CJ-Gateway-APIKey: $tokenNum
                Request Body:
                $requestBodyJson
                ========================================
            """.trimIndent())

            val response = cjWebClient.post()
                .uri(uri)
                .header("CJ-Gateway-APIKey", tokenNum)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono<DeliveryBookingResponse>()
                .block()
                ?: throw IllegalStateException("택배예약접수 응답이 없습니다.")

            val responseJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(response)

            logger.info("""
                ========================================
                CJ 택배예약접수 API 응답
                ========================================
                RESULT_CD: ${response.resultCd}
                Response Body:
                $responseJson
                ========================================
            """.trimIndent())

            response

        } catch (e: Exception) {
            logger.error("CJ 택배예약접수 실패: ${e.message}", e)
            throw IllegalStateException("택배예약접수에 실패했습니다: ${e.message}", e)
        }
    }

    // ==================== 배송조회 관련 ====================
    
    fun getTrackingInfo(invoiceNo: String): TrackingResponse {
        return try {
            logger.info("CJ 배송조회 API 호출 - 운송장번호: $invoiceNo")

            val response = cjWebClient.get()
                .uri("/$invoiceNo")
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .block()
                ?: throw IllegalStateException("응답 데이터가 없습니다.")

            logger.info("CJ 배송조회 API 응답 성공")
            parseTrackingResponse(invoiceNo, response)

        } catch (e: Exception) {
            logger.error("CJ 배송조회 API 호출 실패: ${e.message}", e)
            throw IllegalStateException("배송 조회에 실패했습니다: ${e.message}", e)
        }
    }

    private fun parseTrackingResponse(invoiceNo: String, response: Map<String, Any>): TrackingResponse {
        return try {
            val state = response["state"] as? Map<String, Any>
            val from = response["from"] as? Map<String, Any>
            val to = response["to"] as? Map<String, Any>
            val progresses = response["progresses"] as? List<Map<String, Any>> ?: emptyList()

            TrackingResponse(
                carrier = "CJ대한통운",
                invoiceNo = invoiceNo,
                status = (state?.get("text") as? String) ?: "조회 중",
                senderName = from?.get("name") as? String,
                receiverName = to?.get("name") as? String,
                from = from?.get("location") as? String,
                to = to?.get("location") as? String,
                currentLocation = progresses.lastOrNull()?.get("location") as? String,
                deliveredAt = progresses.lastOrNull()?.get("time") as? String,
                trackingDetails = progresses.map { progress ->
                    TrackingDetail(
                        status = (progress["status"] as? Map<String, Any>)?.get("text") as? String ?: "",
                        time = progress["time"] as? String ?: "",
                        location = progress["location"] as? String ?: "",
                        description = progress["description"] as? String ?: ""
                    )
                }
            )
        } catch (e: Exception) {
            logger.error("응답 파싱 실패", e)
            throw IllegalStateException("응답 데이터 파싱에 실패했습니다.", e)
        }
    }

    private fun maskApiKey(apiKey: String): String {
        return if (apiKey.length > 8) {
            "${apiKey.take(8)}...${apiKey.takeLast(4)}"
        } else {
            "****"
        }
    }
}
