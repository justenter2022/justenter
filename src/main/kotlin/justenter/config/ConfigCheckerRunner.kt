package justenter.config

import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class ConfigChecker(private val cjConfig: CjDeliveryConfig) : CommandLineRunner {
    override fun run(vararg args: String) {
        println("=== CJ Delivery Config ===")
        println("baseUrl: ${cjConfig.baseUrl}")
        println("apiKey: ${maskApiKey(cjConfig.apiKey)}")  // ← 마스킹 처리
        println("timeout: ${cjConfig.timeout}")
        println("custId: ${cjConfig.custId}")
        println("bizRegNum: ${cjConfig.bizRegNum}")
        println("=========================")
    }

    private fun maskApiKey(apiKey: String): String {
        return if (apiKey.length > 8) {
            "${apiKey.take(8)}...${apiKey.takeLast(4)}"
        } else {
            "****"
        }
    }
}
