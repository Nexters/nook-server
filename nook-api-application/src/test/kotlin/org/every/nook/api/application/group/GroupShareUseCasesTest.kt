package org.every.nook.api.application.group

import org.every.nook.api.application.group.error.ShareLinkExpiredException
import org.every.nook.api.application.group.error.ShareLinkRevokedException
import org.every.nook.api.application.group.port.GroupSharePort
import org.every.nook.api.application.group.port.SharedPostViewerQueryPort
import org.every.nook.api.application.post.model.PlaceParsingStatusView
import org.every.nook.api.application.post.model.SavedPostDetail
import org.every.nook.api.application.post.model.SavedPostGroup
import org.every.nook.api.application.post.model.SavedPostPage
import org.every.nook.api.application.post.port.SavedPostQueryPort
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GroupShareUseCasesTest {
    @Test
    fun `active share link resolves shared group`() {
        val access = SharedGroupAccess(1, 2, 3, "token")
        val expected = SharedGroupView(
            GroupView(2, "카페", "YELLOW", 1),
            GroupOwnerView("Purr", null),
        )
        val port = FakeGroupSharePort(resolveResult = GroupSharePort.ResolveResult.Active(access), group = expected)

        assertEquals(expected, GetSharedGroupUseCase(port)("token"))
    }

    @Test
    fun `revoked and expired links keep distinct errors`() {
        assertFailsWith<ShareLinkRevokedException> {
            GetSharedGroupUseCase(FakeGroupSharePort(GroupSharePort.ResolveResult.Revoked))("token")
        }
        assertFailsWith<ShareLinkExpiredException> {
            GetSharedGroupUseCase(FakeGroupSharePort(GroupSharePort.ResolveResult.Expired))("token")
        }
    }

    @Test
    fun `subscription is skipped for group owner`() {
        val access = SharedGroupAccess(1, 2, 7, "token")
        val port = FakeGroupSharePort(GroupSharePort.ResolveResult.Active(access))

        SubscribeSharedGroupUseCase(port)(memberId = 7, token = "token")

        assertEquals(0, port.subscribeCount)
    }

    @Test
    fun `shared post detail exposes only current viewer groups`() {
        val access = SharedGroupAccess(1, 2, 7, "token")
        val sharePort = FakeGroupSharePort(GroupSharePort.ResolveResult.Active(access), containsPost = true)
        val viewerGroups = listOf(SavedPostGroup(99, "내 카페", "YELLOW"))
        val useCase = GetSharedPostDetailUseCase(
            sharePort,
            FakeSavedPostQueryPort(savedPostDetail(groups = listOf(SavedPostGroup(2, "공유 카페", "BLUE")))),
            SharedPostViewerQueryPort { viewerId, _ -> if (viewerId == 10L) viewerGroups else emptyList() },
        )

        assertEquals(viewerGroups, useCase("token", 3, viewerId = 10).groups)
        assertEquals(emptyList(), useCase("token", 3, viewerId = null).groups)
    }
}

private class FakeGroupSharePort(
    private val resolveResult: GroupSharePort.ResolveResult,
    private val group: SharedGroupView? = null,
    private val containsPost: Boolean = false,
) : GroupSharePort {
    var subscribeCount = 0

    override fun resolve(token: String) = resolveResult
    override fun findGroup(access: SharedGroupAccess) = group
    override fun subscribe(memberId: Long, access: SharedGroupAccess): Boolean {
        subscribeCount++
        return true
    }

    override fun issue(ownerId: Long, groupId: Long, expiresAt: Instant?): GroupShareLinkView? = null
    override fun revoke(ownerId: Long, groupId: Long) = false
    override fun findSubscribedGroups(memberId: Long) = emptyList<GroupView>()
    override fun unsubscribe(memberId: Long, groupId: Long) = false
    override fun resolveMemberAccess(memberId: Long, groupId: Long): SharedGroupAccess? = null
    override fun containsPost(access: SharedGroupAccess, savedPostId: Long) = containsPost
    override fun containsPlace(access: SharedGroupAccess, placeId: Long) = false
}

private fun savedPostDetail(groups: List<SavedPostGroup>) = SavedPostDetail(
    postId = 3,
    title = "게시물",
    body = null,
    authorIdentifier = null,
    canonicalUrl = "https://example.com/post",
    publishedAt = null,
    media = emptyList(),
    hashtags = emptyList(),
    memo = "공유자의 메모",
    savedAt = Instant.EPOCH,
    groups = groups,
    placeParsingStatus = PlaceParsingStatusView.COMPLETED,
    placeParsingFailureReason = null,
    places = emptyList(),
)

private class FakeSavedPostQueryPort(private val detail: SavedPostDetail) : SavedPostQueryPort {
    override fun findAll(userId: Long, page: Int, size: Int) = SavedPostPage(emptyList(), page, size, 0, 0, false)
    override fun findDetail(userId: Long, postId: Long) = detail
}
