package org.every.nook.api.admin

import org.every.nook.api.application.providerusage.ExternalProviderBillingPeriod
import org.every.nook.api.application.providerusage.GetExternalProviderBillingUseCase
import org.every.nook.api.application.providerusage.GetExternalProviderOverviewUseCase
import org.every.nook.api.application.providerusage.GetOpenAiTokenUsageUseCase
import org.every.nook.api.application.providerusage.OpenAiTokenUsagePeriod
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/admin/v1/external-provider-usage")
class AdminExternalProviderUsageController(
    private val getOverview: GetExternalProviderOverviewUseCase,
    private val getBilling: GetExternalProviderBillingUseCase,
    private val getOpenAiTokenUsage: GetOpenAiTokenUsageUseCase,
) {
    @GetMapping("/billing")
    fun billing(@RequestParam from: LocalDate, @RequestParam to: LocalDate): ApiResponse<*> =
        ApiResponse.success(getBilling(ExternalProviderBillingPeriod(from, to)))

    @GetMapping("/overview")
    fun overview(): ApiResponse<*> = ApiResponse.success(getOverview())

    @GetMapping("/openai-tokens")
    fun openAiTokens(@RequestParam from: LocalDate, @RequestParam to: LocalDate): ApiResponse<*> =
        ApiResponse.success(getOpenAiTokenUsage(OpenAiTokenUsagePeriod(from, to)))
}
