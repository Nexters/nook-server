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
)

data class AppVersionPolicyView(
    val updateType: AppUpdateType,
    val latestBuildNumber: Long?,
    val latestVersion: String?,
    val storeUrl: String?,
)
