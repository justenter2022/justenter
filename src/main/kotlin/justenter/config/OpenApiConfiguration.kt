package justenter.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("JustEnter API Gateway")
                    .version("v1.0.0")
                    .description("""
                        ## JustEnter 통합 API
                        
                        ### 제공 API
                        - **CJ 대한통운 택배 API**: 운송장 조회 및 배송 추적
                        - (추후 확장 가능)
                        
                        ### 기술 스택
                        - Kotlin 2.2
                        - Spring Boot 4.0.3
                        - Java 21
                    """.trimIndent())
                    .contact(
                        Contact()
                            .name("JustEnter Dev Team")
                            .email("justenter2023@gmail.com")
                    )
                    .license(
                        License()
                            .name("Apache 2.0")
                            .url("https://www.apache.org/licenses/LICENSE-2.0")
                    )
            )
            .servers(
                listOf(
                    Server().url("http://localhost:8080").description("Local Server")
//                    Server().url("https://api.justenter.com").description("Production Server")
                )
            )
    }
}