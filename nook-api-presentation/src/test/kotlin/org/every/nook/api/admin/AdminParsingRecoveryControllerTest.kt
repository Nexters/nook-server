package org.every.nook.api.admin

import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.AdminFollowUpJob
import org.every.nook.api.application.admin.AdminFollowUpPage
import org.every.nook.api.application.admin.AdminParsingRecoveryPort
import org.every.nook.api.application.admin.ListAdminFollowUpJobsUseCase
import org.every.nook.api.application.admin.RetryAdminFollowUpJobUseCase
import org.every.nook.api.application.admin.RetryParsingJobCommand
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AdminParsingRecoveryControllerTest {
    private val port = FakePort()
    private val controller = AdminParsingRecoveryController(
        ListAdminFollowUpJobsUseCase(port),
        RetryAdminFollowUpJobUseCase(port),
    )
    private val actor = AdminActor("operator", "ops@example.com")

    @Test
    fun `list forwards bounded cursor query and maps response`() {
        val page = controller.list(42, "FAILED", 100, 20).success!!
        assertEquals(listOf(42L, "FAILED", 100L, 20), port.query)
        assertEquals("POST_MEDIA", page.jobs.single().type)
        assertEquals(true, page.hasNext)
        assertFailsWith<IllegalArgumentException> { controller.list(0, null, null, 20) }
        assertFailsWith<IllegalArgumentException> { controller.list(null, "INVALID", null, 20) }
        assertFailsWith<IllegalArgumentException> { controller.list(null, null, null, 101) }
    }

    @Test
    fun `retry requires reason and uses authenticated actor`() {
        assertFailsWith<IllegalArgumentException> {
            controller.retry(1, AdminParsingRecoveryController.RetryRequest("  "), actor)
        }
        assertNull(port.command)
        controller.retry(1, AdminParsingRecoveryController.RetryRequest("  서비스 복구  "), actor)
        assertEquals(actor, port.command?.actor)
        assertEquals("서비스 복구", port.command?.reason)
    }

    private class FakePort : AdminParsingRecoveryPort {
        var query: List<Any?>? = null
        var command: RetryParsingJobCommand? = null
        private val job = AdminFollowUpJob(
            1,
            42,
            "POST_MEDIA",
            "FAILED",
            4,
            "failure",
            null,
            Instant.parse("2026-09-13T00:00:00Z"),
        )

        override fun list(postId: Long?, status: String?, beforeId: Long?, limit: Int): AdminFollowUpPage {
            query = listOf(postId, status, beforeId, limit)
            return AdminFollowUpPage(listOf(job), true)
        }

        override fun retry(command: RetryParsingJobCommand): AdminFollowUpJob {
            this.command = command
            return job.copy(status = "PENDING")
        }
    }
}
