package org.every.nook.api.admin

import org.every.nook.api.application.providerusage.ExternalProviderUsageQuery
import org.every.nook.api.application.providerusage.GetExternalProviderUsageUseCase
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/admin/v1/external-provider-usage")
class AdminExternalProviderUsageController(private val getUsage: GetExternalProviderUsageUseCase) {
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

    private companion object {
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 500
    }
}
