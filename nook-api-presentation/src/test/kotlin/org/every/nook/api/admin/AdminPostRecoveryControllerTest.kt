package org.every.nook.api.admin

import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.AdminPostRecovery
import org.every.nook.api.application.admin.AdminPostRecoveryPage
import org.every.nook.api.application.admin.AdminPostRecoveryPort
import org.every.nook.api.application.admin.GetAdminPostRecoveryUseCase
import org.every.nook.api.application.admin.ListAdminPostRecoveryUseCase
import org.every.nook.api.application.admin.ParsingRecoveryException
import org.every.nook.api.application.admin.RetryAdminPostRecoveryUseCase
import org.every.nook.api.application.admin.RetryPostParsingCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdminPostRecoveryControllerTest {
    private val port = FakePort()
    private val controller = AdminPostRecoveryController(
        ListAdminPostRecoveryUseCase(port),
        GetAdminPostRecoveryUseCase(port),
        RetryAdminPostRecoveryUseCase(port),
    )

    @Test
    fun `post queries validate cursor and preserve grouped response`() {
        assertEquals(616L, controller.get(616).success!!.postId)
        controller.list(616, "FAILED", 700, 20)
        assertEquals(listOf(616L, "FAILED", 700L, 20), port.query)
        assertFailsWith<IllegalArgumentException> { controller.list(null, "invalid", null, 20) }
        assertFailsWith<IllegalArgumentException> { controller.list(null, null, 0, 20) }
        assertFailsWith<IllegalArgumentException> { controller.list(null, null, null, 101) }
        assertFailsWith<ParsingRecoveryException> { controller.get(1) }
    }

    @Test
    fun `post retry preserves actor and validates reason`() {
        val actor = AdminActor("operator", "ops@example.com")
        assertFailsWith<IllegalArgumentException> {
            controller.retry(616, AdminPostRecoveryController.RetryRequest(" "), actor)
        }
        controller.retry(616, AdminPostRecoveryController.RetryRequest("  원인 조치  "), actor)
        assertEquals(RetryPostParsingCommand(616, actor, "원인 조치"), port.command)
    }

    private class FakePort : AdminPostRecoveryPort {
        var query: List<Any?> = emptyList()
        var command: RetryPostParsingCommand? = null
        override fun list(postId: Long?, status: String?, beforePostId: Long?, limit: Int): AdminPostRecoveryPage {
            query = listOf(postId, status, beforePostId, limit)
            return AdminPostRecoveryPage(listOf(AdminPostRecovery(616, emptyList())), false)
        }
        override fun find(postId: Long) = AdminPostRecovery(616, emptyList()).takeIf { postId == 616L }
        override fun retry(command: RetryPostParsingCommand): AdminPostRecovery {
            this.command = command
            return requireNotNull(find(command.postId))
        }
    }
}
