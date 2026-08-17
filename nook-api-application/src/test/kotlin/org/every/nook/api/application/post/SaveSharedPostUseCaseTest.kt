package org.every.nook.api.application.post

import org.every.nook.api.application.group.GroupShareLinkView
import org.every.nook.api.application.group.GroupView
import org.every.nook.api.application.group.SharedGroupAccess
import org.every.nook.api.application.group.SharedGroupView
import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.error.SharedResourceNotFoundException
import org.every.nook.api.application.group.port.GroupOwnershipPort
import org.every.nook.api.application.group.port.GroupSharePort
import org.every.nook.api.application.post.port.SaveSharedPostPort
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SaveSharedPostUseCaseTest {
    @Test
    fun `shared post is saved into viewer owned groups`() {
        val savePort = RecordingSaveSharedPostPort()
        val useCase = SaveSharedPostUseCase(
            groupSharePort = FakeGroupSharePort(containsPost = true),
            groupOwnershipPort = GroupOwnershipPort { userId, groupIds -> userId == 10L && groupIds == setOf(3L) },
            saveSharedPostPort = savePort,
        )

        val result = useCase(SaveSharedPostUseCase.Command(10, "token", 20, listOf(3)))

        assertEquals(30, result.postId)
        assertEquals(RecordingSaveSharedPostPort.Call(10, 20, setOf(3)), savePort.call)
    }

    @Test
    fun `saving rejects groups not owned by viewer`() {
        val useCase = SaveSharedPostUseCase(
            groupSharePort = FakeGroupSharePort(containsPost = true),
            groupOwnershipPort = GroupOwnershipPort { _, _ -> false },
            saveSharedPostPort = RecordingSaveSharedPostPort(),
        )

        assertFailsWith<GroupNotFoundException> {
            useCase(SaveSharedPostUseCase.Command(10, "token", 20, listOf(3)))
        }
    }

    @Test
    fun `saving rejects post outside shared group`() {
        val useCase = SaveSharedPostUseCase(
            groupSharePort = FakeGroupSharePort(containsPost = false),
            groupOwnershipPort = GroupOwnershipPort { _, _ -> true },
            saveSharedPostPort = RecordingSaveSharedPostPort(),
        )

        assertFailsWith<SharedResourceNotFoundException> {
            useCase(SaveSharedPostUseCase.Command(10, "token", 20, listOf(3)))
        }
    }
}

private class RecordingSaveSharedPostPort : SaveSharedPostPort {
    var call: Call? = null

    override fun save(userId: Long, sharedPostId: Long, groupIds: Set<Long>): Long {
        call = Call(userId, sharedPostId, groupIds)
        return 30
    }

    data class Call(val userId: Long, val sharedPostId: Long, val groupIds: Set<Long>)
}

private class FakeGroupSharePort(private val containsPost: Boolean) : GroupSharePort {
    private val access = SharedGroupAccess(1, 2, 7, "token")

    override fun resolve(token: String) = GroupSharePort.ResolveResult.Active(access)
    override fun containsPost(access: SharedGroupAccess, savedPostId: Long) = containsPost
    override fun issue(ownerId: Long, groupId: Long, expiresAt: Instant?): GroupShareLinkView? = null
    override fun revoke(ownerId: Long, groupId: Long) = false
    override fun findGroup(access: SharedGroupAccess): SharedGroupView? = null
    override fun findSubscribedGroups(memberId: Long) = emptyList<GroupView>()
    override fun subscribe(memberId: Long, access: SharedGroupAccess) = false
    override fun unsubscribe(memberId: Long, groupId: Long) = false
    override fun resolveMemberAccess(memberId: Long, groupId: Long): SharedGroupAccess? = null
    override fun containsPlace(access: SharedGroupAccess, placeId: Long) = false
}
