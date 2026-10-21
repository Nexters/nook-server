package org.every.nook.api.application.group

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.port.GroupPort
import org.every.nook.api.application.group.port.GroupReadAccessPort
import org.every.nook.api.application.group.port.GroupSubscribersPort
import org.every.nook.api.domain.group.GroupColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GroupSubscribersUseCasesTest {
    @Test
    fun `owner and subscriber read the same page but unrelated member cannot query it`() {
        val subscribers = RecordingGroupSubscribersPort()
        val useCase = ListGroupSubscribersUseCase(
            GroupReadAccessPort { memberId, _ -> 1L.takeIf { memberId in setOf(1L, 2L) } },
            subscribers,
        )
        listOf(1L, 2L).forEach { member ->
            assertEquals(subscribers.page, useCase(ListGroupSubscribersUseCase.Query(member, 17, 0, 20)))
        }
        assertFailsWith<GroupNotFoundException> {
            useCase(ListGroupSubscribersUseCase.Query(3, 17, 0, 20))
        }
        assertEquals(2, subscribers.pageCalls)
    }

    @Test
    fun `owned and subscribed group summaries are fetched in one batch with zero defaults`() {
        val groups = listOf(GroupView(1, "owned", "YELLOW", 0), GroupView(2, "shared", "GREEN", 0))
        val groupPort = groupPort(groups)
        val subscribers = RecordingGroupSubscribersPort().apply {
            summaries = mapOf(2L to GroupSubscriberSummary(5, page.items))
        }
        val result = ListGroupsUseCase(groupPort, subscribers)(1)
        assertEquals(setOf(1L, 2L), subscribers.requestedGroupIds)
        assertEquals(0, result.first().subscriberCount)
        assertEquals(emptyList(), result.first().subscriberPreview)
        assertEquals(5, result.last().subscriberCount)
        assertEquals(subscribers.page.items, result.last().subscriberPreview)
        assertEquals(0, ListOwnedGroupsUseCase(groupPort, subscribers)(1).single().subscriberCount)
        assertEquals(
            5,
            UpdateGroupUseCase(groupPort, subscribers)(UpdateGroupUseCase.Command(1, 2, "new", "GREEN"))
                .subscriberCount,
        )
    }

    private fun groupPort(groups: List<GroupView>) = object : GroupPort {
        override fun findAll(userId: Long) = groups
        override fun findOwned(userId: Long) = groups.take(1)
        override fun create(userId: Long, name: String, color: GroupColor, description: String?): GroupView =
            error("unused")
        override fun update(userId: Long, groupId: Long, name: String, color: GroupColor, description: String?) =
            GroupPort.UpdateResult.Updated(groups.last())
        override fun delete(userId: Long, groupId: Long) = false
    }
}

internal class RecordingGroupSubscribersPort : GroupSubscribersPort {
    var summaries: Map<Long, GroupSubscriberSummary> = emptyMap()
    var requestedGroupIds: Set<Long> = emptySet()
    var pageCalls = 0
    val page = GroupSubscriberPage(listOf(GroupSubscriberView("reader", null)), 0, 20, 1, 1, false)

    override fun findSummaries(groupIds: Set<Long>): Map<Long, GroupSubscriberSummary> {
        requestedGroupIds = groupIds
        return summaries
    }

    override fun findPage(groupId: Long, page: Int, size: Int): GroupSubscriberPage {
        pageCalls++
        return this.page
    }
}
