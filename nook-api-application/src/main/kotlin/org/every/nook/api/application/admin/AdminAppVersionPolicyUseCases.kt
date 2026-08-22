package org.every.nook.api.application.admin

import org.every.nook.api.application.appversion.AppPlatform
import org.every.nook.api.application.appversion.AppVersionPolicy

class ListAdminAppVersionPoliciesUseCase(private val port: AdminAppVersionPolicyPort) {
    operator fun invoke(): List<AppVersionPolicy> = port.findAll()
}

class UpsertAdminAppVersionPolicyUseCase(private val port: AdminAppVersionPolicyPort) {
    operator fun invoke(command: Command): AppVersionPolicy {
        require(command.minimumSupportedBuildNumber >= 0) { "Minimum supported build number must not be negative" }
        require(command.latestBuildNumber >= command.minimumSupportedBuildNumber) {
            "Latest build number must be greater than or equal to minimum supported build number"
        }
        val latestVersion = command.latestVersion.trim()
        val storeUrl = command.storeUrl.trim()
        require(latestVersion.isNotEmpty()) { "Latest version must not be blank" }
        require(latestVersion.length <= AppVersionPolicy.MAX_VERSION_LENGTH) { "Latest version is too long" }
        require(storeUrl.startsWith("https://")) { "Store URL must use HTTPS" }
        require(storeUrl.length <= AppVersionPolicy.MAX_STORE_URL_LENGTH) { "Store URL is too long" }
        require(command.reason.isNotBlank()) { "Change reason must not be blank" }

        return port.upsert(
            AdminAppVersionPolicyPort.UpsertCommand(
                platform = command.platform,
                minimumSupportedBuildNumber = command.minimumSupportedBuildNumber,
                latestBuildNumber = command.latestBuildNumber,
                latestVersion = latestVersion,
                storeUrl = storeUrl,
                actor = command.actor,
                reason = command.reason.trim(),
                requestId = command.requestId,
            ),
        )
    }

    data class Command(
        val platform: AppPlatform,
        val minimumSupportedBuildNumber: Long,
        val latestBuildNumber: Long,
        val latestVersion: String,
        val storeUrl: String,
        val actor: AdminActor,
        val reason: String,
        val requestId: String?,
    )
}
