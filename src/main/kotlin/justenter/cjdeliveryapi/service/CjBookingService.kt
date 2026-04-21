package justenter.cjdeliveryapi.service

import justenter.cjdeliveryapi.client.CjApiClient
import justenter.cjdeliveryapi.dto.request.BookingGoodsItem
import justenter.cjdeliveryapi.dto.request.DeliveryBookingRequest
import justenter.cjdeliveryapi.dto.response.DeliveryBookingResponse
import justenter.common.dto.GoodsItem
import justenter.config.CjDeliveryConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class CjBookingService(
    private val cjApiClient: CjApiClient,
    private val cjTokenService: CjTokenService,
    private val cjConfig: CjDeliveryConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun registerBooking(
        custUseNo: String,
        invcNo: String,
        sendrNm: String,
        sendrTelNo1: String,
        sendrTelNo2: String,
        sendrTelNo3: String,
        sendrZipNo: String,
        sendrAddr: String,
        sendrDetailAddr: String,
        rcvrNm: String,
        rcvrTelNo1: String,
        rcvrTelNo2: String,
        rcvrTelNo3: String,
        rcvrZipNo: String,
        rcvrAddr: String,
        rcvrDetailAddr: String,
        goods: List<GoodsItem>
    ): DeliveryBookingResponse {
        logger.info("택배예약접수 서비스 시작 - 운송장번호: $invcNo, 고객사용번호: $custUseNo")

        val tokenNum = cjTokenService.getOrRefreshToken()
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val mpckKey = "${today}_${cjConfig.custId}_${custUseNo}"

        val goodsItems = goods.mapIndexed { index, item ->
            BookingGoodsItem(
                mpckSeq = (index + 1).toString(),
                gdsNm = item.productType,
                gdsQty = item.qty,
                gdsAmt = item.amount
            )
        }

        val request = DeliveryBookingRequest(
            tokenNum = tokenNum,
            custId = cjConfig.custId,
            rcptYmd = today,
            custUseNo = custUseNo,
            mpckKey = mpckKey,
            custMgmtDlcmCd = cjConfig.custId,
            invcNo = invcNo,
            sendrNm = sendrNm,
            sendrTelNo1 = sendrTelNo1,
            sendrTelNo2 = sendrTelNo2,
            sendrTelNo3 = sendrTelNo3,
            sendrZipNo = sendrZipNo,
            sendrAddr = sendrAddr,
            sendrDetailAddr = sendrDetailAddr,
            rcvrNm = rcvrNm,
            rcvrTelNo1 = rcvrTelNo1,
            rcvrTelNo2 = rcvrTelNo2,
            rcvrTelNo3 = rcvrTelNo3,
            rcvrZipNo = rcvrZipNo,
            rcvrAddr = rcvrAddr,
            rcvrDetailAddr = rcvrDetailAddr,
            array = goodsItems
        )

        val response = cjApiClient.requestBooking(tokenNum, request)

        if (response.resultCd == "S") {
            logger.info("택배예약접수 성공 - 운송장번호: $invcNo")
        } else {
            logger.warn("택배예약접수 실패 - RESULT_CD: ${response.resultCd}, DETAIL: ${response.resultDetail}")
        }

        return response
    }
}
