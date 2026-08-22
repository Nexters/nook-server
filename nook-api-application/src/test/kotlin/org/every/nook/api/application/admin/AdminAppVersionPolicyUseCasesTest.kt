package org.every.nook.api.application.admin

import org.every.nook.api.application.appversion.AppPlatform
import org.every.nook.api.application.appversion.AppVersionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdminAppVersionPolicyUseCasesTest {
    private val actor = AdminActor("admin-subject", "admin@example.com")

    @Test
    fun `lists platform policies`() {
        val port = RecordingAdminAppVersionPolicyPort()
        port.policies += policy(AppPlatform.IOS)

        val result = ListAdminAppVersionPoliciesUseCase(port)()

        assertEquals(listOf(AppPlatform.IOS), result.map { it.platform })
    }

    @Test
    fun `trims and upserts a valid policy`() {
        val port = RecordingAdminAppVersionPolicyPort()

        val result = UpsertAdminAppVersionPolicyUseCase(port)(
            command(latestVersion = " 1.2.0 ", storeUrl = " https://example.com/store ", reason = " release "),
        )

        assertEquals("1.2.0", result.latestVersion)
        assertEquals("https://example.com/store", result.storeUrl)
        assertEquals("release", port.lastCommand?.reason)
    }

    @Test
    fun `rejects latest build below minimum supported build`() {
        assertFailsWith<IllegalArgumentException> {
            UpsertAdminAppVersionPolicyUseCase(RecordingAdminAppVersionPolicyPort())(
                command(minimumSupportedBuildNumber = 20, latestBuildNumber = 19),
            )
        }
    }

    @Test
    fun `rejects non HTTPS store URL`() {
        assertFailsWith<IllegalArgumentException> {
            UpsertAdminAppVersionPolicyUseCase(RecordingAdminAppVersionPolicyPort())(
                command(storeUrl = "http://example.com/store"),
            )
        }
    }

    private fun command(
        minimumSupportedBuildNumber: Long = 10,
        latestBuildNumber: Long = 20,
        latestVersion: String = "1.2.0",
        storeUrl: String = "https://example.com/store",
        reason: String = "release",
    ) = UpsertAdminAppVersionPolicyUseCase.Command(
        platform = AppPlatform.IOS,
        minimumSupportedBuildNumber = minimumSupportedBuildNumber,
        latestBuildNumber = latestBuildNumber,
        latestVersion = latestVersion,
        storeUrl = storeUrl,
        actor = actor,
        reason = reason,
        requestId = "request-id",
    )

    private fun policy(platform: AppPlatform) = AppVersionPolicy(platform, 10, 20, "1.2.0", "https://example.com")

    private class RecordingAdminAppVersionPolicyPort : AdminAppVersionPolicyPort {
        val policies = mutableListOf<AppVersionPolicy>()
        var lastCommand: AdminAppVersionPolicyPort.UpsertCommand? = null

        override fun findAll(): List<AppVersionPolicy> = policies

        override fun upsert(command: AdminAppVersionPolicyPort.UpsertCommand): AppVersionPolicy {
            lastCommand = command
            return AppVersionPolicy(
                command.platform,
                command.minimumSupportedBuildNumber,
                command.latestBuildNumber,
                command.latestVersion,
                command.storeUrl,
            )
        }
    }
}
