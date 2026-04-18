package justenter

import justenter.config.CjDeliveryConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(CjDeliveryConfig::class)
class JustenterApplication

fun main(args: Array<String>) {
    runApplication<JustenterApplication>(*args)
}