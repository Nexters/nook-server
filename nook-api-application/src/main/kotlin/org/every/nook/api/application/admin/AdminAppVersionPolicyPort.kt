package org.every.nook.api.application.admin

import org.every.nook.api.application.appversion.AppPlatform
import org.every.nook.api.application.appversion.AppVersionPolicy

interface AdminAppVersionPolicyPort {
    fun findAll(): List<AppVersionPolicy>

    fun upsert(command: UpsertCommand): AppVersionPolicy

    data class UpsertCommand(
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
