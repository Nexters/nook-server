package org.every.nook.api.infrastructure.persistence.group

import org.every.nook.api.application.group.port.GroupPort
import org.every.nook.api.domain.group.GroupColor
import org.mockito.Mockito.mock
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
    fun `deleting an owned group delegates to database cascade`() {
        `when`(groupRepository.deleteByIdAndUserId(17, 7)).thenReturn(1)

        assertTrue(adapter.delete(7, 17))
        verify(groupRepository).deleteByIdAndUserId(17, 7)
    }

    @Test
    fun `deleting another users group changes nothing`() {
        `when`(groupRepository.deleteByIdAndUserId(17, 7)).thenReturn(0)

        assertFalse(adapter.delete(7, 17))
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
}
