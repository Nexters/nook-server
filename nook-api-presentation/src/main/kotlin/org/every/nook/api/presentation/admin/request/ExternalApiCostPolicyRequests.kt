package org.every.nook.api.presentation.admin.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Pattern
import java.math.BigDecimal

data class SaveExternalApiPriceRequest(
    @field:Schema(description = "단위 크기당 원화 단가")
    @field:DecimalMin("0")
    val unitPriceKrw: BigDecimal,
    @field:Schema(description = "단가가 적용되는 사용량 단위 크기")
    @field:DecimalMin(value = "0", inclusive = false)
    val unitSize: BigDecimal,
    @field:Schema(description = "단가 정책 활성 여부")
    val enabled: Boolean = true,
)

data class SaveExternalApiBudgetRequest(
    @field:Schema(description = "월간 원화 예산")
    @field:DecimalMin("0")
    val monthlyBudgetKrw: BigDecimal,
    @field:Schema(description = "예산 정책 모드", allowableValues = ["ALERT_ONLY", "BLOCK"])
    @field:Pattern(regexp = "ALERT_ONLY|BLOCK")
    val mode: String,
    @field:Schema(description = "예산 정책 활성 여부")
    val enabled: Boolean = true,
)
