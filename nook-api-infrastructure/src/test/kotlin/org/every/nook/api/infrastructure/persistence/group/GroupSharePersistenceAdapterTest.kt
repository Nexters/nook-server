package org.every.nook.api.infrastructure.persistence.group

import org.every.nook.api.application.group.SharedGroupAccess
import org.every.nook.api.infrastructure.persistence.member.MemberJpaRepository
import org.every.nook.api.infrastructure.persistence.place.SharedPlaceBookmarkSyncJpaRepository
import org.every.nook.api.infrastructure.persistence.save.SharedGroupContentJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupSharePersistenceAdapterTest {
    private val shareLinkRepository = mock(GroupShareLinkJpaRepository::class.java)
    private val subscriptionRepository = mock(SharedGroupSubscriptionJpaRepository::class.java)
    private val groupRepository = mock(GroupJpaRepository::class.java)
    private val groupPostRepository = mock(GroupPostJpaRepository::class.java)
    private val savedPostRepository = mock(UserSavedPostJpaRepository::class.java)
    private val sharedContentRepository = mock(SharedGroupContentJpaRepository::class.java)
    private val memberRepository = mock(MemberJpaRepository::class.java)
    private val bookmarkRepository = mock(SharedPlaceBookmarkSyncJpaRepository::class.java)
    private val adapter = GroupSharePersistenceAdapter(
        shareLinkRepository,
        subscriptionRepository,
        groupRepository,
        groupPostRepository,
        savedPostRepository,
        sharedContentRepository,
        memberRepository,
        bookmarkRepository,
    )

    @Test
    fun `subscription creates bookmarks for current shared group places`() {
        `when`(subscriptionRepository.existsByMemberIdAndShareLinkId(10, 1)).thenReturn(false)

        assertTrue(adapter.subscribe(memberId = 10, access = ACCESS))

        verify(bookmarkRepository).insertAllFromSharedGroup(memberId = 10, groupId = 17)
    }

    @Test
    fun `place belongs to the shared group when native exists returns one`() {
        `when`(sharedContentRepository.existsPlaceInGroup(7, 17, 27)).thenReturn(1L)

        assertTrue(adapter.containsPlace(ACCESS, placeId = 27))
        verify(sharedContentRepository).existsPlaceInGroup(7, 17, 27)
    }

    @Test
    fun `place does not belong to the shared group when native exists returns zero`() {
        `when`(sharedContentRepository.existsPlaceInGroup(7, 17, 27)).thenReturn(0L)

        assertFalse(adapter.containsPlace(ACCESS, placeId = 27))
        verify(sharedContentRepository).existsPlaceInGroup(7, 17, 27)
    }

    private companion object {
        val ACCESS = SharedGroupAccess(
            shareLinkId = 1,
            groupId = 17,
            ownerId = 7,
            token = "share-token",
        )
    }
}
