package org.every.nook.api.infrastructure.persistence.group

import org.every.nook.api.application.group.port.GroupPort
import org.every.nook.api.domain.group.GroupColor
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupPersistenceAdapterTest {
    private val groupRepository = mock(GroupJpaRepository::class.java)
    private val groupPostRepository = mock(GroupPostJpaRepository::class.java)
    private val adapter = GroupPersistenceAdapter(groupRepository, groupPostRepository)

    @Test
    fun `duplicate group name is rejected within a user`() {
        `when`(groupRepository.existsByUserIdAndName(7, "카페")).thenReturn(true)

        val created = adapter.create(7, "카페", GroupColor.YELLOW)

        assertNull(created)
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
    fun `updated summary includes current post count`() {
        `when`(groupRepository.existsByIdAndUserId(17, 7)).thenReturn(true)
        `when`(groupRepository.existsByUserIdAndNameAndIdNot(7, "서울 카페", 17)).thenReturn(false)
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
