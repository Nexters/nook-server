package org.every.nook.api.application.appversion

fun interface AppVersionPolicyPort {
    fun findByPlatform(platform: AppPlatform): AppVersionPolicy?
}
