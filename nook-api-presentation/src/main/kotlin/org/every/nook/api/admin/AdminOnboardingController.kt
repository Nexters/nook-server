package org.every.nook.api.admin

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.every.nook.api.application.onboarding.GetOnboardingConversionUseCase
import org.every.nook.api.application.onboarding.OnboardingPeriod
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@Tag(name = "AdminOnboarding")
@RestController
@RequestMapping("/api/admin/v1/onboarding-conversion")
class AdminOnboardingController(private val getConversion: GetOnboardingConversionUseCase) {
    @Operation(summary = "온보딩 Instagram CTA 이후 첫 저장 전환 조회")
    @GetMapping
    fun overview(
        @Parameter(description = "최초 CTA 발생일 조회 시작일 KST") @RequestParam from: LocalDate,
        @Parameter(description = "최초 CTA 발생일 조회 종료일 KST, 포함. 최대 90일.") @RequestParam to: LocalDate,
    ): ApiResponse<OnboardingConversionResponse> = ApiResponse.success(
        OnboardingConversionResponse.from(getConversion(OnboardingPeriod(from, to))),
    )
}
