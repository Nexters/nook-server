package org.every.nook.api.application.group

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.port.GroupPostManagementPort
import org.every.nook.api.application.post.error.PostNotFoundException
import org.every.nook.api.application.post.model.SavedPostDetail
import org.every.nook.api.application.post.model.SavedPostPage
import org.every.nook.api.application.post.port.SavedPostQueryPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GroupPostUseCasesTest {
    @Test
    fun `lists posts only from a group owned by the user`() {
        val port = FakeSavedPostQueryPort(
            groupPage = SavedPostPage(emptyList(), page = 1, size = 10, 0, 0, false),
        )

        val result = ListGroupPostsUseCase(port)(
            ListGroupPostsUseCase.Query(userId = 7, groupId = 17, page = 1, size = 10),
        )

        assertEquals(listOf(7L, 17L, 1L, 10L), port.captured)
        assertEquals(1, result.page)
    }

    @Test
    fun `inaccessible group is exposed as not found`() {
        assertFailsWith<GroupNotFoundException> {
            ListGroupPostsUseCase(FakeSavedPostQueryPort(groupPage = null))(
                ListGroupPostsUseCase.Query(userId = 7, groupId = 17, page = 0, size = 20),
            )
        }
    }

    @Test
    fun `replaces groups as a deduplicated set`() {
        val port = FakeGroupPostManagementPort(GroupPostManagementPort.ReplaceResult.Updated)

        ReplaceSavedPostGroupsUseCase(port)(
            ReplaceSavedPostGroupsUseCase.Command(
                userId = 7,
                savedPostId = 11,
                groupIds = listOf(17, 18, 17),
            ),
        )

        assertEquals(setOf(17L, 18L), port.groupIds)
    }

    @Test
    fun `reports inaccessible post and group without exposing ownership`() {
        assertFailsWith<PostNotFoundException> {
            ReplaceSavedPostGroupsUseCase(
                FakeGroupPostManagementPort(GroupPostManagementPort.ReplaceResult.PostNotFound),
            )(ReplaceSavedPostGroupsUseCase.Command(7, 11, listOf(17)))
        }
        assertFailsWith<GroupNotFoundException> {
            ReplaceSavedPostGroupsUseCase(
                FakeGroupPostManagementPort(GroupPostManagementPort.ReplaceResult.GroupNotFound),
            )(ReplaceSavedPostGroupsUseCase.Command(7, 11, listOf(17)))
        }
    }

    private class FakeGroupPostManagementPort(private val result: GroupPostManagementPort.ReplaceResult) :
        GroupPostManagementPort {
        var groupIds: Set<Long>? = null

        override fun replace(
            userId: Long,
            savedPostId: Long,
            groupIds: Set<Long>,
        ): GroupPostManagementPort.ReplaceResult {
            this.groupIds = groupIds
            return result
        }
    }

    private class FakeSavedPostQueryPort(private val groupPage: SavedPostPage?) : SavedPostQueryPort {
        var captured: List<Long>? = null

        override fun findAll(userId: Long, page: Int, size: Int): SavedPostPage = error("not used")

        override fun findAllByGroup(userId: Long, groupId: Long, page: Int, size: Int): SavedPostPage? {
            captured = listOf(userId, groupId, page.toLong(), size.toLong())
            return groupPage
        }

        override fun findDetail(userId: Long, postId: Long): SavedPostDetail? = error("not used")
    }
}
