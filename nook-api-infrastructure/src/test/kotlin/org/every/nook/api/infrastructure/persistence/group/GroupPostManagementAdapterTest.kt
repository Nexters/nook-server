package org.every.nook.api.infrastructure.persistence.group

import org.every.nook.api.application.group.port.GroupPostManagementPort
import org.every.nook.api.infrastructure.persistence.place.SharedPlaceBookmarkSyncJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupPostManagementAdapterTest {
    private val groupRepository = mock(GroupJpaRepository::class.java)
    private val groupPostRepository = mock(GroupPostJpaRepository::class.java)
    private val savedPostRepository = mock(UserSavedPostJpaRepository::class.java)
    private val bookmarkRepository = mock(SharedPlaceBookmarkSyncJpaRepository::class.java)
    private val adapter = GroupPostManagementAdapter(
        groupRepository = groupRepository,
        groupPostRepository = groupPostRepository,
        savedPostRepository = savedPostRepository,
        bookmarkRepository = bookmarkRepository,
        clock = FIXED_CLOCK,
    )

    @Test
    fun `replaces existing links with owned groups`() {
        val savedPost = mock(UserSavedPostEntity::class.java)
        val firstGroup = group(17)
        val secondGroup = group(18)
        `when`(savedPostRepository.findByIdAndUserId(11, 7)).thenReturn(savedPost)
        `when`(groupRepository.findAllByUserIdAndIdIn(7, setOf(17, 18)))
            .thenReturn(listOf(firstGroup, secondGroup))

        val result = adapter.replace(userId = 7, savedPostId = 11, groupIds = setOf(17, 18))

        assertEquals(GroupPostManagementPort.ReplaceResult.Updated, result)
        verify(groupPostRepository).softDeleteAllByUserSavedPostId(11, FIXED_NOW)
        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<GroupPostEntity>>
        verify(groupPostRepository).saveAll(captor.capture())
        assertEquals(setOf(17L, 18L), captor.value.map { it.groupId }.toSet())
        verify(bookmarkRepository).insertAllForActiveSubscribers(11, setOf(17, 18))
    }

    @Test
    fun `empty groups remove every existing link`() {
        `when`(savedPostRepository.findByIdAndUserId(11, 7)).thenReturn(mock(UserSavedPostEntity::class.java))

        val result = adapter.replace(userId = 7, savedPostId = 11, groupIds = emptySet())

        assertEquals(GroupPostManagementPort.ReplaceResult.Updated, result)
        verify(groupPostRepository).softDeleteAllByUserSavedPostId(11, FIXED_NOW)
        verify(groupPostRepository).saveAll(emptyList<GroupPostEntity>())
    }

    @Test
    fun `does not mutate links for inaccessible posts or groups`() {
        `when`(savedPostRepository.findByIdAndUserId(11, 7)).thenReturn(null)
        assertEquals(
            GroupPostManagementPort.ReplaceResult.PostNotFound,
            adapter.replace(userId = 7, savedPostId = 11, groupIds = setOf(17)),
        )
        verifyNoInteractions(groupPostRepository)

        val secondGroupPostRepository = mock(GroupPostJpaRepository::class.java)
        val secondAdapter = GroupPostManagementAdapter(
            groupRepository,
            secondGroupPostRepository,
            savedPostRepository,
            bookmarkRepository,
        )
        `when`(savedPostRepository.findByIdAndUserId(12, 7)).thenReturn(mock(UserSavedPostEntity::class.java))
        `when`(groupRepository.findAllByUserIdAndIdIn(7, setOf(17))).thenReturn(emptyList())
        assertEquals(
            GroupPostManagementPort.ReplaceResult.GroupNotFound,
            secondAdapter.replace(userId = 7, savedPostId = 12, groupIds = setOf(17)),
        )
        verifyNoInteractions(secondGroupPostRepository)
    }

    @Test
    fun `validates every post and group before bulk replacement`() {
        val first = mock(UserSavedPostEntity::class.java)
        val second = mock(UserSavedPostEntity::class.java)
        `when`(first.id).thenReturn(11)
        `when`(second.id).thenReturn(12)
        `when`(savedPostRepository.findAllByUserIdAndIdIn(7, setOf(11, 12))).thenReturn(listOf(first, second))
        val destinationGroup = group(17)
        `when`(groupRepository.findAllByUserIdAndIdIn(7, setOf(17))).thenReturn(listOf(destinationGroup))

        val result = adapter.replaceAll(7, setOf(11, 12), setOf(17))

        assertEquals(GroupPostManagementPort.ReplaceResult.Updated, result)
        verify(groupPostRepository).softDeleteAllByUserSavedPostId(11, FIXED_NOW)
        verify(groupPostRepository).softDeleteAllByUserSavedPostId(12, FIXED_NOW)
        verify(bookmarkRepository).insertAllForActiveSubscribers(11, setOf(17))
        verify(bookmarkRepository).insertAllForActiveSubscribers(12, setOf(17))
    }

    @Test
    fun `does not partially replace when a bulk post is inaccessible`() {
        val first = mock(UserSavedPostEntity::class.java)
        `when`(first.id).thenReturn(11)
        `when`(savedPostRepository.findAllByUserIdAndIdIn(7, setOf(11, 12))).thenReturn(listOf(first))

        val result = adapter.replaceAll(7, setOf(11, 12), setOf(17))

        assertEquals(GroupPostManagementPort.ReplaceResult.PostNotFound, result)
        verifyNoInteractions(groupPostRepository)
    }

    private fun group(id: Long): GroupEntity {
        val group = mock(GroupEntity::class.java)
        `when`(group.id).thenReturn(id)
        return group
    }

    private companion object {
        val FIXED_NOW: Instant = Instant.parse("2026-08-01T00:00:00Z")
        val FIXED_CLOCK: Clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
    }
}
