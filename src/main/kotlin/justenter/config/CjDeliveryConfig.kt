package justenter.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cj.delivery")
data class CjDeliveryConfig(
    val baseUrl: String,
    val apiKey: String,
    val timeout: Long = 10000,
    val custId: String,
    val bizRegNum: String
)