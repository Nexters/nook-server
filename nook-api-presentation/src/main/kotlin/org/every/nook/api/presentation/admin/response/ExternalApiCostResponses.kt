package org.every.nook.api.presentation.admin.response

import io.swagger.v3.oas.annotations.media.Schema
import org.every.nook.api.application.billing.ExternalApiBudgetPolicy
import org.every.nook.api.application.billing.ExternalApiCostDashboard
import org.every.nook.api.application.billing.ExternalApiCostPolicies
import org.every.nook.api.application.billing.ExternalApiPricePolicy
import org.every.nook.api.application.billing.ExternalApiProviderCost
import org.every.nook.api.application.billing.ExternalApiUsageSummary
import java.math.BigDecimal
import java.time.Instant

data class ExternalApiCostDashboardResponse(
    @field:Schema(description = "조회 시작 시각") val from: Instant,
    @field:Schema(description = "조회 종료 시각") val to: Instant,
    @field:Schema(description = "전체 외부 API 호출 수") val totalCallCount: Long,
    @field:Schema(description = "전체 예상 원화 비용") val totalEstimatedCostKrw: BigDecimal,
    @field:Schema(description = "제공자별 비용 및 예산 현황") val providers: List<Provider>,
) {
    data class Provider(
        @field:Schema(description = "외부 API 제공자") val provider: String,
        @field:Schema(description = "호출 수") val callCount: Long,
        @field:Schema(description = "예상 원화 비용") val estimatedCostKrw: BigDecimal,
        @field:Schema(description = "월간 원화 예산") val monthlyBudgetKrw: BigDecimal?,
        @field:Schema(description = "예산 사용률") val budgetUsagePercent: BigDecimal?,
        @field:Schema(description = "예산 정책 모드") val budgetMode: String?,
        @field:Schema(description = "예산 상태") val status: String,
    ) {
        companion object {
            fun from(source: ExternalApiProviderCost) = Provider(
                source.provider,
                source.callCount,
                source.estimatedCostKrw,
                source.monthlyBudgetKrw,
                source.budgetUsagePercent,
                source.budgetMode,
                source.status.name,
            )
        }
    }

    companion object {
        fun from(source: ExternalApiCostDashboard) = ExternalApiCostDashboardResponse(
            source.from,
            source.to,
            source.totalCallCount,
            source.totalEstimatedCostKrw,
            source.providers.map(Provider::from),
        )
    }
}

data class ExternalApiUsageSummaryResponse(
    @field:Schema(description = "외부 API 제공자") val provider: String,
    @field:Schema(description = "과금 SKU") val sku: String,
    @field:Schema(description = "호출 기능") val feature: String,
    @field:Schema(description = "호출 수") val callCount: Long,
    @field:Schema(description = "총 사용량") val totalUnits: BigDecimal,
    @field:Schema(description = "예상 원화 비용") val estimatedCostKrw: BigDecimal,
) {
    companion object {
        fun from(source: ExternalApiUsageSummary) = ExternalApiUsageSummaryResponse(
            source.provider,
            source.sku,
            source.feature,
            source.callCount,
            source.totalUnits,
            source.estimatedCostKrw,
        )
    }
}

data class ExternalApiCostPoliciesResponse(
    @field:Schema(description = "SKU별 단가 정책") val prices: List<Price>,
    @field:Schema(description = "제공자별 월간 예산 정책") val budgets: List<Budget>,
) {
    data class Price(
        @field:Schema(description = "외부 API 제공자") val provider: String,
        @field:Schema(description = "과금 SKU") val sku: String,
        @field:Schema(description = "원화 단가") val unitPriceKrw: BigDecimal,
        @field:Schema(description = "가격 단위 크기") val unitSize: BigDecimal,
        @field:Schema(description = "월 무료 사용량") val freeMonthlyUnits: BigDecimal,
        @field:Schema(description = "공식 가격 출처") val sourceUrl: String?,
        @field:Schema(description = "공식 가격 통화") val sourceCurrency: String,
        @field:Schema(description = "공식 통화 기준 단가") val sourceUnitPrice: BigDecimal,
        @field:Schema(description = "시스템 관리 기본 단가 여부") val managed: Boolean,
        @field:Schema(description = "활성 여부") val enabled: Boolean,
    ) {
        companion object {
            fun from(source: ExternalApiPricePolicy) = Price(
                source.provider,
                source.sku,
                source.unitPriceKrw,
                source.unitSize,
                source.freeMonthlyUnits,
                source.sourceUrl,
                source.sourceCurrency,
                source.sourceUnitPrice,
                source.managed,
                source.enabled,
            )
        }
    }

    data class Budget(
        @field:Schema(description = "외부 API 제공자") val provider: String,
        @field:Schema(description = "월간 원화 예산") val monthlyBudgetKrw: BigDecimal,
        @field:Schema(description = "예산 정책 모드") val mode: String,
        @field:Schema(description = "활성 여부") val enabled: Boolean,
    ) {
        companion object {
            fun from(source: ExternalApiBudgetPolicy) =
                Budget(source.provider, source.monthlyBudgetKrw, source.mode, source.enabled)
        }
    }

    companion object {
        fun from(source: ExternalApiCostPolicies) = ExternalApiCostPoliciesResponse(
            source.prices.map(Price::from),
            source.budgets.map(Budget::from),
        )
    }
}
