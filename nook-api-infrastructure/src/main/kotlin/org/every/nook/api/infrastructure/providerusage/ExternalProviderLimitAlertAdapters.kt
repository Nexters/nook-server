package org.every.nook.api.infrastructure.providerusage

import org.every.nook.api.application.providerusage.ExternalProviderLimitAlertCandidate
import org.every.nook.api.application.providerusage.ExternalProviderLimitAlertCandidatePort
import org.every.nook.api.application.providerusage.ExternalProviderLimitAlertDeliveryPort
import org.every.nook.api.application.providerusage.ExternalProviderLimitAlertNotifier
import org.every.nook.api.application.providerusage.ExternalProviderSkuUsagePort
import org.every.nook.api.application.providerusage.ExternalProviderSkuUsageQuery
import org.every.nook.api.infrastructure.persistence.providerusage.ExternalProviderLimitNotificationEntity
import org.every.nook.api.infrastructure.persistence.providerusage.ExternalProviderLimitNotificationJpaRepository
import org.every.nook.api.infrastructure.persistence.providerusage.ExternalProviderUsageLimitJpaRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import java.time.Instant
import java.time.LocalDate

@Component
class ExternalProviderLimitAlertPersistenceAdapter(
    private val limits: ExternalProviderUsageLimitJpaRepository,
    private val notifications: ExternalProviderLimitNotificationJpaRepository,
    private val usage: ExternalProviderSkuUsagePort,
) : ExternalProviderLimitAlertCandidatePort,
    ExternalProviderLimitAlertDeliveryPort {
    @Transactional(readOnly = true)
    override fun find(from: Instant, to: Instant, periodStart: LocalDate): List<ExternalProviderLimitAlertCandidate> {
        val skuUsage = usage.get(ExternalProviderSkuUsageQuery(from, to)).skus.associateBy { it.provider to it.sku }
        return limits.findAllByEnabledTrue().mapNotNull { policy ->
            val id = requireNotNull(policy.id)
            val current = skuUsage[policy.provider to policy.sku]
                ?.limits
                ?.firstOrNull { it.id == id }
                ?: return@mapNotNull null
            val notified = notifications.findAllByLimitPolicyIdAndPeriodStart(id, periodStart)
                .map { it.thresholdPercent }
                .toSet()
            ExternalProviderLimitAlertCandidate(
                policyId = id,
                provider = policy.provider,
                sku = policy.sku,
                limitType = policy.limitType,
                monthlyLimit = policy.monthlyLimit,
                currentValue = current.currentValue,
                utilizationPercent = current.utilizationPercent,
                notifiedThresholds = notified,
            )
        }
    }

    @Transactional
    override fun markDelivered(policyId: Long, periodStart: LocalDate, thresholdPercent: Int, notifiedAt: Instant) {
        if (notifications.existsByLimitPolicyIdAndPeriodStartAndThresholdPercent(
                policyId,
                periodStart,
                thresholdPercent,
            )
        ) {
            return
        }
        notifications.save(
            ExternalProviderLimitNotificationEntity(policyId, periodStart, thresholdPercent, notifiedAt),
        )
    }
}

@Component
class SlackExternalProviderLimitAlertNotifier(
    @Value("\${external-provider.limits.slack-webhook-url:}") private val webhookUrl: String,
) : ExternalProviderLimitAlertNotifier {
    private val restClient = RestClient.create()

    override fun notify(candidate: ExternalProviderLimitAlertCandidate, thresholdPercent: Int) {
        check(webhookUrl.startsWith(SLACK_WEBHOOK_PREFIX)) { "External provider limit Slack webhook is not configured" }
        restClient.post()
            .uri(webhookUrl)
            .body(
                mapOf(
                    "text" to "[외부 API 상한 $thresholdPercent%] ${candidate.provider} / ${candidate.sku}\n" +
                        "${candidate.limitType}: ${candidate.currentValue} / ${candidate.monthlyLimit} " +
                        "(${candidate.utilizationPercent}%)",
                ),
            )
            .retrieve()
            .toBodilessEntity()
    }

    private companion object {
        const val SLACK_WEBHOOK_PREFIX = "https://hooks.slack.com/services/"
    }
}
