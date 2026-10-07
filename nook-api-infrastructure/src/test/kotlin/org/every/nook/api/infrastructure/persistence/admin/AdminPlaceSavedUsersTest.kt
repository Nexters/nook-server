package org.every.nook.api.infrastructure.persistence.admin

import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.admin.AdminSavedUser
import org.every.nook.api.infrastructure.persistence.place.PlaceEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostEntity
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostPlaceEntity
import org.every.nook.api.infrastructure.persistence.post.PostPlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceJpaRepository
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminPlaceSavedUsersTest {
    @Test
    fun `linked posts expose their own saved members with one batch lookup`() {
        val places = mock(PlaceJpaRepository::class.java)
        val posts = mock(PostJpaRepository::class.java)
        val mappings = mock(PostPlaceJpaRepository::class.java)
        val savedUsers = mock(AdminSavedUsersQuery::class.java)
        val first = post(1)
        val second = post(2)
        `when`(places.findById(10L)).thenReturn(
            Optional.of(
                PlaceEntity(
                    "KAKAO",
                    "external",
                    "place",
                    "address",
                    latitude = BigDecimal.ONE,
                    longitude = BigDecimal.ONE,
                ),
            ),
        )
        `when`(mappings.findAllByPlaceId(10L)).thenReturn(
            listOf(PostPlaceEntity(1, 10, 0), PostPlaceEntity(2, 10, 1)),
        )
        `when`(posts.findAllById(listOf(1L, 2L))).thenReturn(listOf(first, second))
        val users = listOf(AdminSavedUser(49, "first"), AdminSavedUser(50, "second"))
        `when`(savedUsers.findByPostIds(setOf(1L, 2L))).thenReturn(mapOf(1L to users))
        val adapter = AdminPlacePersistenceAdapter(
            places,
            posts,
            mappings,
            mock(UserSavedPostPlaceJpaRepository::class.java),
            mock(AdminAuditLogPort::class.java),
            jacksonObjectMapper(),
            savedUsers,
        )

        val detail = requireNotNull(adapter.findPlace(10))

        assertEquals(listOf(2L, 1L), detail.posts.map { it.id })
        assertTrue(detail.posts[0].savedUsers.isEmpty())
        assertEquals(users, detail.posts[1].savedUsers)
        verify(savedUsers).findByPostIds(setOf(1L, 2L))
    }

    private fun post(id: Long) = mock(PostEntity::class.java).also {
        `when`(it.id).thenReturn(id)
        `when`(it.canonicalUrl).thenReturn("https://www.instagram.com/p/$id/")
        `when`(it.createdAt).thenReturn(Instant.ofEpochSecond(id))
    }
}
