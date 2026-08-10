package org.every.nook.api.infrastructure.persistence.group

import org.every.nook.api.infrastructure.persistence.member.MemberEntity
import org.every.nook.api.infrastructure.persistence.member.MemberJpaRepository
import org.every.nook.api.infrastructure.persistence.save.GroupPlaceProjection
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroupPlaceQueryPersistenceAdapterTest {
    private val savedPostRepository = mock(UserSavedPostJpaRepository::class.java)
    private val groupRepository = mock(GroupJpaRepository::class.java)
    private val memberRepository = mock(MemberJpaRepository::class.java)
    private val adapter = GroupPlaceQueryPersistenceAdapter(
        savedPostRepository,
        groupRepository,
        memberRepository,
    )

    @Test
    fun `group place list returns owner nickname and distinct place page`() {
        val group = mock(GroupEntity::class.java)
        val owner = mock(MemberEntity::class.java)
        val place = mock(GroupPlaceProjection::class.java)
        val requestedPage = PageRequest.of(0, 20)
        `when`(group.userId).thenReturn(9)
        `when`(owner.nickname).thenReturn("Purr")
        `when`(place.id).thenReturn(31)
        `when`(place.name).thenReturn("퍼머넌트해비탯")
        `when`(place.city).thenReturn("서울")
        `when`(place.address).thenReturn("서울 마포구 연희로1길 55")
        `when`(place.category).thenReturn("카페")
        `when`(place.latitude).thenReturn(BigDecimal("37.5"))
        `when`(place.longitude).thenReturn(BigDecimal("127.0"))
        `when`(place.thumbnailUrl).thenReturn("https://example.com/place.jpg")
        `when`(place.representativeTags).thenReturn("""["QUIET","COZY"]""")
        `when`(groupRepository.findByIdAndUserId(17, 7)).thenReturn(group)
        `when`(memberRepository.findById(9)).thenReturn(Optional.of(owner))
        `when`(savedPostRepository.findDistinctPlacesByUserIdAndGroupId(7, 17, requestedPage))
            .thenReturn(PageImpl(listOf(place), requestedPage, 1))

        val result = requireNotNull(adapter.findPlaces(userId = 7, groupId = 17, page = 0, size = 20))

        assertEquals("Purr", result.ownerNickname)
        assertEquals(31, result.items.single().id)
        assertEquals("서울", result.items.single().city)
        assertEquals(listOf("조용한", "아늑한"), result.items.single().tags)
        assertEquals(1, result.totalElements)
        verify(savedPostRepository).findDistinctPlacesByUserIdAndGroupId(7, 17, requestedPage)
    }

    @Test
    fun `another users group place list is not returned`() {
        `when`(groupRepository.findByIdAndUserId(17, 7)).thenReturn(null)

        assertNull(adapter.findPlaces(userId = 7, groupId = 17, page = 0, size = 20))
    }
}
