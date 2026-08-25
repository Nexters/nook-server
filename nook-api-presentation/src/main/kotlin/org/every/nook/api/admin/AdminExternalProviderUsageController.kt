package org.every.nook.api.admin

import org.every.nook.api.application.providerusage.ExternalProviderSkuUsageQuery
import org.every.nook.api.application.providerusage.ExternalProviderUsageQuery
import org.every.nook.api.application.providerusage.GetExternalProviderOverviewUseCase
import org.every.nook.api.application.providerusage.GetExternalProviderSkuUsageUseCase
import org.every.nook.api.application.providerusage.GetExternalProviderUsageUseCase
import org.every.nook.api.application.providerusage.SaveExternalProviderLimitCommand
import org.every.nook.api.application.providerusage.SaveExternalProviderLimitUseCase
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/admin/v1/external-provider-usage")
class AdminExternalProviderUsageController(
    private val getUsage: GetExternalProviderUsageUseCase,
    private val getOverview: GetExternalProviderOverviewUseCase,
    private val getSkuUsage: GetExternalProviderSkuUsageUseCase,
    private val saveLimit: SaveExternalProviderLimitUseCase,
) {
    @GetMapping
    fun get(
        @RequestParam from: Instant,
        @RequestParam to: Instant,
        @RequestParam(required = false) provider: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "100") limit: Int,
    ): ApiResponse<*> = ApiResponse.success(
        getUsage(
            ExternalProviderUsageQuery(
                from = from,
                to = to,
                provider = provider?.takeIf { it.isNotBlank() },
                status = status?.takeIf { it.isNotBlank() },
                offset = offset.coerceAtLeast(0),
                limit = limit.coerceIn(MIN_LIMIT, MAX_LIMIT),
            ),
        ),
    )

    @GetMapping("/overview")
    fun overview(@RequestParam from: Instant, @RequestParam to: Instant): ApiResponse<*> = ApiResponse.success(
        getOverview(ExternalProviderUsageQuery(from = from, to = to)),
    )

    @GetMapping("/skus")
    fun skus(@RequestParam from: Instant, @RequestParam to: Instant): ApiResponse<*> = ApiResponse.success(
        getSkuUsage(ExternalProviderSkuUsageQuery(from, to)),
    )

    @PutMapping("/limits")
    fun saveLimit(@RequestBody request: SaveLimitRequest): ApiResponse<*> = ApiResponse.success(
        saveLimit(
            SaveExternalProviderLimitCommand(
                provider = request.provider.trim().uppercase(),
                sku = request.sku.trim().uppercase(),
                limitType = request.limitType.trim().uppercase(),
                monthlyLimit = request.monthlyLimit,
                enabled = request.enabled,
            ),
        ),
    )

    private companion object {
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 500
    }
}

data class SaveLimitRequest(
    val provider: String,
    val sku: String,
    val limitType: String,
    val monthlyLimit: java.math.BigDecimal,
    val enabled: Boolean = true,
)
