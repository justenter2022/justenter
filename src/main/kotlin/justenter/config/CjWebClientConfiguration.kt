package justenter.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
class WebClientConfig(
    private val cjConfig: CjDeliveryConfig
) {

    @Bean
    fun cjWebClient(): WebClient {
        val httpClient = HttpClient.create()
            .responseTimeout(Duration.ofMillis(cjConfig.timeout))

        return WebClient.builder()
            .baseUrl(cjConfig.baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .defaultHeader("CJ-Gateway-APIKey", cjConfig.apiKey)
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .build()
    }

    // 추후 다른 API용 WebClient 추가 가능
    // @Bean
    // fun anotherWebClient(): WebClient { ... }
}