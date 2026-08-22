package org.every.nook.api.application.appversion

enum class AppPlatform {
    IOS,
    ANDROID,
}

enum class AppUpdateType {
    NONE,
    RECOMMEND,
    FORCE,
}

data class AppVersionPolicy(
    val platform: AppPlatform,
    val minimumSupportedBuildNumber: Long,
    val latestBuildNumber: Long,
    val latestVersion: String,
    val storeUrl: String,
) {
    companion object {
        const val MAX_VERSION_LENGTH = 30
        const val MAX_STORE_URL_LENGTH = 500
    }
}

data class AppVersionPolicyView(
    val updateType: AppUpdateType,
    val latestBuildNumber: Long?,
    val latestVersion: String?,
    val storeUrl: String?,
)
