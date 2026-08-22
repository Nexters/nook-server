package org.every.nook.api.application.appversion

class GetAppVersionPolicyUseCase(private val policyPort: AppVersionPolicyPort) {
    operator fun invoke(query: Query): AppVersionPolicyView {
        val policy = policyPort.findByPlatform(query.platform)
            ?: return AppVersionPolicyView(
                updateType = AppUpdateType.NONE,
                latestBuildNumber = null,
                latestVersion = null,
                storeUrl = null,
            )

        val updateType = when {
            query.buildNumber < policy.minimumSupportedBuildNumber -> AppUpdateType.FORCE
            query.buildNumber < policy.latestBuildNumber -> AppUpdateType.RECOMMEND
            else -> AppUpdateType.NONE
        }

        return AppVersionPolicyView(
            updateType = updateType,
            latestBuildNumber = policy.latestBuildNumber,
            latestVersion = policy.latestVersion,
            storeUrl = policy.storeUrl,
        )
    }

    data class Query(val platform: AppPlatform, val buildNumber: Long)
}
