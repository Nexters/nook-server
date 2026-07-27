package org.every.nook.api.infrastructure.persistence.group

import org.every.nook.api.application.group.port.GroupPostManagementPort
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupPostManagementAdapterTest {
    private val groupRepository = mock(GroupJpaRepository::class.java)
    private val groupPostRepository = mock(GroupPostJpaRepository::class.java)
    private val savedPostRepository = mock(UserSavedPostJpaRepository::class.java)
    private val adapter = GroupPostManagementAdapter(
        groupRepository = groupRepository,
        groupPostRepository = groupPostRepository,
        savedPostRepository = savedPostRepository,
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
        verify(groupPostRepository).deleteAllByUserSavedPostId(11)
        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<GroupPostEntity>>
        verify(groupPostRepository).saveAll(captor.capture())
        assertEquals(setOf(17L, 18L), captor.value.map { it.groupId }.toSet())
    }

    @Test
    fun `empty groups remove every existing link`() {
        `when`(savedPostRepository.findByIdAndUserId(11, 7)).thenReturn(mock(UserSavedPostEntity::class.java))

        val result = adapter.replace(userId = 7, savedPostId = 11, groupIds = emptySet())

        assertEquals(GroupPostManagementPort.ReplaceResult.Updated, result)
        verify(groupPostRepository).deleteAllByUserSavedPostId(11)
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
        val secondAdapter = GroupPostManagementAdapter(groupRepository, secondGroupPostRepository, savedPostRepository)
        `when`(savedPostRepository.findByIdAndUserId(12, 7)).thenReturn(mock(UserSavedPostEntity::class.java))
        `when`(groupRepository.findAllByUserIdAndIdIn(7, setOf(17))).thenReturn(emptyList())
        assertEquals(
            GroupPostManagementPort.ReplaceResult.GroupNotFound,
            secondAdapter.replace(userId = 7, savedPostId = 12, groupIds = setOf(17)),
        )
        verifyNoInteractions(secondGroupPostRepository)
    }

    private fun group(id: Long): GroupEntity {
        val group = mock(GroupEntity::class.java)
        `when`(group.id).thenReturn(id)
        return group
    }
}
