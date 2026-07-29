package org.every.nook.api.infrastructure.persistence.group

import org.every.nook.api.application.group.port.GroupPort
import org.every.nook.api.domain.group.GroupColor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GroupPersistenceAdapterTest {
    private val groupRepository = mock(GroupJpaRepository::class.java)
    private val groupPostRepository = mock(GroupPostJpaRepository::class.java)
    private val adapter = GroupPersistenceAdapter(groupRepository, groupPostRepository)

    @Test
    fun `lists groups with recent thumbnail urls in repository order`() {
        val groupSummary = mock(GroupSummaryProjection::class.java)
        val firstThumbnail = mock(GroupThumbnailProjection::class.java)
        val secondThumbnail = mock(GroupThumbnailProjection::class.java)
        `when`(groupSummary.id).thenReturn(17)
        `when`(groupSummary.name).thenReturn("카페")
        `when`(groupSummary.color).thenReturn(GroupColor.YELLOW)
        `when`(groupSummary.postCount).thenReturn(4)
        `when`(firstThumbnail.groupId).thenReturn(17)
        `when`(firstThumbnail.thumbnailUrl).thenReturn("https://example.com/latest.jpg")
        `when`(secondThumbnail.groupId).thenReturn(17)
        `when`(secondThumbnail.thumbnailUrl).thenReturn("https://example.com/second.jpg")
        `when`(groupRepository.findAllSummaries(7)).thenReturn(listOf(groupSummary))
        `when`(groupRepository.findRecentThumbnailUrls(7)).thenReturn(listOf(firstThumbnail, secondThumbnail))

        val result = adapter.findAll(7)

        assertEquals(
            listOf("https://example.com/latest.jpg", "https://example.com/second.jpg"),
            result.single().thumbnailUrls,
        )
        verify(groupRepository).findRecentThumbnailUrls(7)
    }

    @Test
    fun `does not query thumbnails when user has no groups`() {
        `when`(groupRepository.findAllSummaries(7)).thenReturn(emptyList())

        assertTrue(adapter.findAll(7).isEmpty())
        verify(groupRepository, never()).findRecentThumbnailUrls(7)
    }

    @Test
    fun `duplicate group name is saved as a distinct group`() {
        val firstSaved = mock(GroupEntity::class.java)
        val secondSaved = mock(GroupEntity::class.java)
        `when`(firstSaved.id).thenReturn(17)
        `when`(firstSaved.name).thenReturn("카페")
        `when`(firstSaved.color).thenReturn(GroupColor.YELLOW)
        `when`(secondSaved.id).thenReturn(18)
        `when`(secondSaved.name).thenReturn("카페")
        `when`(secondSaved.color).thenReturn(GroupColor.GREEN)
        `when`(groupRepository.saveAndFlush(any(GroupEntity::class.java)))
            .thenReturn(firstSaved, secondSaved)

        val first = adapter.create(7, "카페", GroupColor.YELLOW)
        val second = adapter.create(7, "카페", GroupColor.GREEN)

        assertEquals(17, first.id)
        assertEquals(18, second.id)
        assertEquals("카페", first.name)
        assertEquals("카페", second.name)
    }

    @Test
    fun `another users group cannot be updated`() {
        `when`(groupRepository.existsByIdAndUserId(17, 7)).thenReturn(false)

        val result = adapter.update(7, 17, "카페", GroupColor.GREEN)

        assertIs<GroupPort.UpdateResult.NotFound>(result)
    }

    @Test
    fun `deleting an owned group deletes links before the group`() {
        `when`(groupRepository.existsByIdAndUserId(17, 7)).thenReturn(true)
        `when`(groupRepository.deleteByIdAndUserId(17, 7)).thenReturn(1)

        assertTrue(adapter.delete(7, 17))
        val ordered = inOrder(groupPostRepository, groupRepository)
        ordered.verify(groupPostRepository).deleteAllByGroupId(17)
        ordered.verify(groupRepository).deleteByIdAndUserId(17, 7)
    }

    @Test
    fun `deleting another users group changes nothing`() {
        `when`(groupRepository.existsByIdAndUserId(17, 7)).thenReturn(false)

        assertFalse(adapter.delete(7, 17))
        verify(groupPostRepository, never()).deleteAllByGroupId(17)
        verify(groupRepository, never()).deleteByIdAndUserId(17, 7)
    }

    @Test
    fun `updates to a duplicate group name and includes current post count`() {
        `when`(groupRepository.existsByIdAndUserId(17, 7)).thenReturn(true)
        `when`(groupRepository.updateByIdAndUserId(17, 7, "서울 카페", GroupColor.GREEN)).thenReturn(1)
        `when`(groupPostRepository.countByGroupId(17)).thenReturn(3)

        val result = adapter.update(7, 17, "서울 카페", GroupColor.GREEN)

        val updated = assertIs<GroupPort.UpdateResult.Updated>(result)
        assertEquals(3, updated.group.postCount)
        assertEquals("GREEN", updated.group.color)
    }

    @Test
    fun `owns all requested groups only when every group belongs to the user`() {
        val firstGroup = mock(GroupEntity::class.java)
        val secondGroup = mock(GroupEntity::class.java)
        `when`(firstGroup.id).thenReturn(17)
        `when`(secondGroup.id).thenReturn(18)
        `when`(groupRepository.findAllByUserIdAndIdIn(7, setOf(17, 18)))
            .thenReturn(listOf(firstGroup, secondGroup))

        assertTrue(adapter.ownsAll(7, setOf(17, 18)))
    }

    @Test
    fun `does not own requested groups when one is missing or belongs to another user`() {
        val ownedGroup = mock(GroupEntity::class.java)
        `when`(ownedGroup.id).thenReturn(17)
        `when`(groupRepository.findAllByUserIdAndIdIn(7, setOf(17, 18))).thenReturn(listOf(ownedGroup))

        assertFalse(adapter.ownsAll(7, setOf(17, 18)))
    }
}
