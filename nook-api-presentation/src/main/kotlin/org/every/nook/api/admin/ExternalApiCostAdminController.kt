package org.every.nook.api.admin

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.every.nook.api.application.billing.ExternalApiUsageQuery
import org.every.nook.api.application.billing.GetExternalApiCostDashboardUseCase
import org.every.nook.api.application.billing.GetExternalApiCostPoliciesUseCase
import org.every.nook.api.application.billing.GetExternalApiUsageSummaryUseCase
import org.every.nook.api.application.billing.SaveExternalApiBudgetCommand
import org.every.nook.api.application.billing.SaveExternalApiBudgetUseCase
import org.every.nook.api.application.billing.SaveExternalApiPriceCommand
import org.every.nook.api.application.billing.SaveExternalApiPriceUseCase
import org.every.nook.api.presentation.admin.request.SaveExternalApiBudgetRequest
import org.every.nook.api.presentation.admin.request.SaveExternalApiPriceRequest
import org.every.nook.api.presentation.admin.response.ExternalApiCostDashboardResponse
import org.every.nook.api.presentation.admin.response.ExternalApiCostPoliciesResponse
import org.every.nook.api.presentation.admin.response.ExternalApiUsageSummaryResponse
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

@Tag(name = "Admin External API Cost")
@Validated
@RestController
@RequestMapping("/api/admin/v1/external-api-costs")
class ExternalApiCostAdminController(
    private val getDashboard: GetExternalApiCostDashboardUseCase,
    private val getUsage: GetExternalApiUsageSummaryUseCase,
    private val getPolicies: GetExternalApiCostPoliciesUseCase,
    private val savePrice: SaveExternalApiPriceUseCase,
    private val saveBudget: SaveExternalApiBudgetUseCase,
    private val clock: Clock,
) {
    @Operation(summary = "외부 API 비용 대시보드 조회")
    @GetMapping("/dashboard")
    fun dashboard(@RequestParam(required = false) month: YearMonth?): ApiResponse<ExternalApiCostDashboardResponse> {
        val targetMonth = month ?: YearMonth.now(clock)
        val query = targetMonth.toQuery()
        return ApiResponse.success(ExternalApiCostDashboardResponse.from(getDashboard(query)))
    }

    @Operation(summary = "외부 API 사용량 집계 조회")
    @GetMapping("/usage")
    fun usage(
        @RequestParam from: Instant,
        @RequestParam to: Instant,
        @RequestParam(required = false) provider: String?,
    ): ApiResponse<List<ExternalApiUsageSummaryResponse>> = ApiResponse.success(
        getUsage(ExternalApiUsageQuery(from, to, provider)).map(ExternalApiUsageSummaryResponse::from),
    )

    @Operation(summary = "외부 API 단가 및 예산 정책 조회")
    @GetMapping("/policies")
    fun policies(): ApiResponse<ExternalApiCostPoliciesResponse> =
        ApiResponse.success(ExternalApiCostPoliciesResponse.from(getPolicies()))

    @Operation(summary = "외부 API SKU 단가 설정")
    @PutMapping("/prices/{provider}/{sku}")
    fun savePrice(
        @PathVariable @NotBlank provider: String,
        @PathVariable @NotBlank sku: String,
        @Valid @RequestBody request: SaveExternalApiPriceRequest,
    ): ApiResponse<ExternalApiCostPoliciesResponse.Price> {
        val saved = savePrice(
            SaveExternalApiPriceCommand(provider, sku, request.unitPriceKrw, request.unitSize, request.enabled),
        )
        return ApiResponse.success(ExternalApiCostPoliciesResponse.Price.from(saved))
    }

    @Operation(summary = "외부 API 제공자 월간 예산 설정")
    @PutMapping("/budgets/{provider}")
    fun saveBudget(
        @PathVariable @NotBlank provider: String,
        @Valid @RequestBody request: SaveExternalApiBudgetRequest,
    ): ApiResponse<ExternalApiCostPoliciesResponse.Budget> {
        val saved = saveBudget(
            SaveExternalApiBudgetCommand(provider, request.monthlyBudgetKrw, request.mode, request.enabled),
        )
        return ApiResponse.success(ExternalApiCostPoliciesResponse.Budget.from(saved))
    }

    private fun YearMonth.toQuery(): ExternalApiUsageQuery {
        val from = atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val to = plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        return ExternalApiUsageQuery(from, to)
    }
}
