package org.every.nook.api.infrastructure.persistence.admin

import org.every.nook.api.application.admin.AdminSavedUser
import org.every.nook.api.infrastructure.persistence.place.PlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostEntity
import org.every.nook.api.infrastructure.persistence.post.PostHashtagJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostMediaJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostPlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.save.SavedPostUserProjection
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.jpa.repository.Query
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminPostSavedUsersTest {
    private val postRepository = mock(PostJpaRepository::class.java)
    private val savedPostRepository = mock(UserSavedPostJpaRepository::class.java)
    private val adapter = AdminPersistenceAdapter(
        postRepository = postRepository,
        mediaRepository = mock(PostMediaJpaRepository::class.java),
        hashtagRepository = mock(PostHashtagJpaRepository::class.java),
        contentJobRepository = mock(PostContentParsingJobJpaRepository::class.java),
        placeJobRepository = mock(PlaceParsingJobJpaRepository::class.java),
        postPlaceRepository = mock(PostPlaceJpaRepository::class.java),
        placeRepository = mock(PlaceJpaRepository::class.java),
        savedPostRepository = savedPostRepository,
        reviewRepository = mock(PostPlaceReviewJpaRepository::class.java),
        auditRepository = mock(AdminAuditLogJpaRepository::class.java),
        objectMapper = jacksonObjectMapper(),
    )

    @Test
    fun `page includes only its saved users with matching counts and one lookup`() {
        val posts = listOf(post(1), post(2), post(3), post(4))
        `when`(postRepository.findAll()).thenReturn(posts)
        `when`(savedPostRepository.findActiveSavedUsersByPostIdIn(listOf(3L, 2L, 1L))).thenReturn(
            listOf(savedUser(2, 49, "first"), savedUser(3, 49, "first"), savedUser(3, 50, "second")),
        )

        val page = adapter.listPosts(null, null, 1, 3)

        assertEquals(4L, page.total)
        assertEquals(listOf(3L, 2L, 1L), page.items.map { it.id })
        assertEquals(listOf(AdminSavedUser(49, "first"), AdminSavedUser(50, "second")), page.items[0].savedUsers)
        assertEquals(listOf(AdminSavedUser(49, "first")), page.items[1].savedUsers)
        assertTrue(page.items[2].savedUsers.isEmpty())
        assertEquals(listOf(2L, 1L, 0L), page.items.map { it.savedUserCount })
        verify(savedPostRepository).findActiveSavedUsersByPostIdIn(listOf(3L, 2L, 1L))
        page.items.forEach { verify(savedPostRepository, never()).countDistinctActiveUsersByPostId(it.id) }
    }

    @Test
    fun `empty page does not query saved users`() {
        val posts = listOf(post(1))
        `when`(postRepository.findAll()).thenReturn(posts)

        val page = adapter.listPosts(null, null, 1, 10)

        assertTrue(page.items.isEmpty())
        assertEquals(1L, page.total)
        verify(savedPostRepository, never()).findActiveSavedUsersByPostIdIn(emptyList())
    }

    @Test
    fun `saved user query excludes deleted saves missing members and posts outside page`() {
        val dataSource = DriverManagerDataSource("jdbc:h2:mem:admin-post-saved-users;MODE=MySQL")
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE members (id BIGINT, nickname VARCHAR(100))")
                statement.execute(
                    "CREATE TABLE user_saved_posts (post_id BIGINT, user_id BIGINT, deleted_at TIMESTAMP)",
                )
                statement.execute("INSERT INTO members VALUES (49, 'first'), (50, 'second')")
                statement.execute(
                    "INSERT INTO user_saved_posts VALUES " +
                        "(3, 50, NULL), (3, 49, NULL), (3, 49, NULL), (2, 49, NULL), " +
                        "(2, 50, CURRENT_TIMESTAMP), (1, 50, NULL), (3, 99, NULL)",
                )
            }
            val query = UserSavedPostJpaRepository::class.java
                .getMethod("findActiveSavedUsersByPostIdIn", Collection::class.java)
                .getAnnotation(Query::class.java).value
            val rows = NamedParameterJdbcTemplate(JdbcTemplate(dataSource)).query(
                query,
                mapOf("postIds" to listOf(2L, 3L)),
            ) { result, _ -> Triple(result.getLong("postId"), result.getLong("id"), result.getString("nickname")) }

            assertEquals(listOf(Triple(2L, 49L, "first"), Triple(3L, 49L, "first"), Triple(3L, 50L, "second")), rows)
        }
    }

    private fun post(id: Long): PostEntity = mock(PostEntity::class.java).also {
        `when`(it.id).thenReturn(id)
        `when`(it.canonicalUrl).thenReturn("https://www.instagram.com/p/$id/")
        `when`(it.createdAt).thenReturn(Instant.ofEpochSecond(id))
    }

    private fun savedUser(postId: Long, id: Long, nickname: String) = object : SavedPostUserProjection {
        override val postId = postId
        override val id = id
        override val nickname = nickname
    }
}
