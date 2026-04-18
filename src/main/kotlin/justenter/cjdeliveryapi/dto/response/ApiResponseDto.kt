package justenter.cjdeliveryapi.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "API 공통 응답")
data class ApiResponse<T>(
    @Schema(description = "성공 여부")
    val success: Boolean,
    
    @Schema(description = "응답 메시지")
    val message: String,
    
    @Schema(description = "응답 데이터")
    val data: T? = null,
    
    @Schema(description = "응답 시간")
    val timestamp: LocalDateTime = LocalDateTime.now()
)
