package org.every.nook.api.infrastructure.persistence.appversion

import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.AdminAppVersionPolicyPort
import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.appversion.AppPlatform
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppVersionPolicyPersistenceAdapterTest {
    @Test
    fun `creates policy and appends audit log`() {
        val repository = mock(AppVersionPolicyJpaRepository::class.java)
        val auditLogPort = RecordingAuditLogPort()
        `when`(repository.findByPlatform(AppPlatform.IOS)).thenReturn(null)
        `when`(repository.save(any(AppVersionPolicyEntity::class.java))).thenAnswer { it.arguments.first() }
        val adapter = AppVersionPolicyPersistenceAdapter(repository, auditLogPort, jacksonObjectMapper())

        val result = adapter.upsert(command())

        assertEquals(10, result.minimumSupportedBuildNumber)
        val audit = auditLogPort.entries.single()
        assertEquals("APP_VERSION_POLICY_UPSERTED", audit.action)
        assertEquals("APP_VERSION_POLICY", audit.targetType)
        assertEquals("IOS", audit.targetId)
        assertNull(audit.beforeValue)
    }

    @Test
    fun `updates existing platform policy`() {
        val repository = mock(AppVersionPolicyJpaRepository::class.java)
        val auditLogPort = RecordingAuditLogPort()
        val entity = AppVersionPolicyEntity(AppPlatform.IOS, 1, 2, "1.0.0", "https://example.com/old")
        `when`(repository.findByPlatform(AppPlatform.IOS)).thenReturn(entity)
        val adapter = AppVersionPolicyPersistenceAdapter(repository, auditLogPort, jacksonObjectMapper())

        val result = adapter.upsert(command())

        assertEquals(10, entity.minimumSupportedBuildNumber)
        assertEquals(20, entity.latestBuildNumber)
        assertEquals("1.2.0", result.latestVersion)
        assertEquals("release", auditLogPort.entries.single().reason)
    }

    private fun command() = AdminAppVersionPolicyPort.UpsertCommand(
        platform = AppPlatform.IOS,
        minimumSupportedBuildNumber = 10,
        latestBuildNumber = 20,
        latestVersion = "1.2.0",
        storeUrl = "https://example.com/store",
        actor = AdminActor("subject", "admin@example.com"),
        reason = "release",
        requestId = "request-id",
    )

    private class RecordingAuditLogPort : AdminAuditLogPort {
        val entries = mutableListOf<AdminAuditLogPort.Entry>()

        override fun listAuditLogs(targetType: String?, targetId: String?, offset: Int, limit: Int) = error("Not used")

        override fun append(entry: AdminAuditLogPort.Entry) {
            entries += entry
        }
    }
}
