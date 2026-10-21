package org.every.nook.api.infrastructure.persistence.place

import org.springframework.data.jpa.repository.Query
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.sql.Timestamp
import java.util.UUID
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceArchiveFilterQueryTest {
    private lateinit var connection: Connection
    private lateinit var jdbc: JdbcTemplate
    private lateinit var namedJdbc: NamedParameterJdbcTemplate

    @BeforeTest
    fun setUp() {
        val dataSource = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL")
        connection = dataSource.connection
        jdbc = JdbcTemplate(dataSource)
        namedJdbc = NamedParameterJdbcTemplate(jdbc)
        schema.forEach(jdbc::execute)
        fixtures.forEach(jdbc::execute)
    }

    @AfterTest
    fun tearDown() {
        connection.close()
    }

    @Test
    fun `both queries select owned and subscribed archives with OR and deduplicate places`() {
        assertPlaces(listOf(1L), listOf(1L, 2L))
        assertPlaces(listOf(2L), listOf(2L, 3L))
        assertPlaces(listOf(1L, 2L, 1L), listOf(1L, 2L, 3L))
        assertPlaces(null, listOf(1L, 2L, 3L, 4L))
        assertPlaces(listOf(99L), emptyList())
        assertPlaces(listOf(1L, 99L), listOf(1L, 2L))
    }

    @Test
    fun `revoked expired deleted and unsubscribed archives stop contributing immediately`() {
        listOf(
            "UPDATE group_share_links SET revoked_at = CURRENT_TIMESTAMP",
            "UPDATE group_share_links SET expires_at = TIMESTAMP '2000-01-01 00:00:00'",
            "UPDATE user_groups SET deleted_at = CURRENT_TIMESTAMP WHERE id = 2",
            "UPDATE group_posts SET deleted_at = CURRENT_TIMESTAMP WHERE group_id = 2",
            "UPDATE user_saved_posts SET deleted_at = CURRENT_TIMESTAMP WHERE user_id = 8",
            "DELETE FROM shared_group_subscriptions",
        ).forEach { mutation ->
            jdbc.execute(mutation)
            assertPlaces(listOf(2L), emptyList())
            assertPlaces(listOf(1L, 2L), listOf(1L, 2L))
            jdbc.execute("DROP ALL OBJECTS")
            schema.forEach(jdbc::execute)
            fixtures.forEach(jdbc::execute)
        }
    }

    @Test
    fun `deleted owned archive and membership do not contribute to selected results`() {
        jdbc.execute("UPDATE group_posts SET deleted_at = CURRENT_TIMESTAMP WHERE group_id = 1")
        assertPlaces(listOf(1L), emptyList())
        assertPlaces(null, listOf(1L, 2L, 3L, 4L))
        jdbc.execute("UPDATE group_posts SET deleted_at = NULL")
        jdbc.execute("UPDATE user_groups SET deleted_at = CURRENT_TIMESTAMP WHERE id = 1")
        assertPlaces(listOf(1L), emptyList())
    }

    @Test
    fun `recent pagination filters before limiting and resolves token from selected shared archive`() {
        val first = rows("findRecentPlaces", listOf(2L), limit = 1)
        assertEquals(listOf(3L), first.map { (it.getValue("placeId") as Number).toLong() })
        assertEquals("selected-token", first.single()["shareToken"])
        val next = rows("findRecentPlaces", listOf(2L), cursorId = 3, limit = 1)
        assertEquals(listOf(2L), next.map { (it.getValue("placeId") as Number).toLong() })
        assertEquals(null, next.single()["shareToken"])
        assertEquals(emptyList(), rows("findRecentPlaces", listOf(2L), cursorId = 2, limit = 1))
    }

    @Test
    fun `map bounds still restrict selected archives`() {
        jdbc.execute("UPDATE places SET latitude = 40 WHERE id = 3")
        val ids = rows("findMapPlaces", listOf(2L)).map { (it.getValue("id") as Number).toLong() }
        assertEquals(listOf(2L), ids)
    }

    private fun assertPlaces(groupIds: List<Long>?, expected: List<Long>) {
        listOf("findMapPlaces" to "id", "findRecentPlaces" to "placeId").forEach { (method, column) ->
            val ids = rows(method, groupIds).map { (it.getValue(column) as Number).toLong() }
            assertEquals(expected.sorted(), ids.sorted(), "$method with $groupIds")
            assertEquals(ids.size, ids.toSet().size)
        }
    }

    private fun rows(
        method: String,
        groupIds: List<Long>?,
        cursorId: Long? = null,
        limit: Int = 20,
    ): List<Map<String, Any?>> {
        val query = UserPlaceBookmarkJpaRepository::class.memberFunctions.single { it.name == method }
            .findAnnotation<Query>()!!.value
        return namedJdbc.queryForList(
            query,
            mapOf(
                "userId" to 7L,
                "filterByGroups" to (groupIds != null),
                "groupIds" to (groupIds ?: listOf(0L)),
                "northLatitude" to 38,
                "southLatitude" to 37,
                "westLongitude" to 126,
                "eastLongitude" to 128,
                "cursorBookmarkedAt" to cursorId?.let { Timestamp.valueOf("2026-01-01 00:00:00") },
                "cursorBookmarkId" to cursorId,
                "limit" to limit,
            ),
        )
    }

    private companion object {
        val schema = listOf(
            """
            CREATE TABLE places (
                id BIGINT, name VARCHAR(100), city VARCHAR(100), address VARCHAR(100), category VARCHAR(100),
                provider_category VARCHAR(100), latitude DECIMAL(10,6), longitude DECIMAL(10,6),
                thumbnail_url VARCHAR(100), thumbnail_parsing_status VARCHAR(30), representative_tags VARCHAR(100)
            )
            """,
            """
            CREATE TABLE user_place_bookmarks (
                id BIGINT, user_id BIGINT, place_id BIGINT, last_saved_at TIMESTAMP
            )
            """,
            "CREATE TABLE user_saved_posts (id BIGINT, user_id BIGINT, created_at TIMESTAMP, deleted_at TIMESTAMP)",
            "CREATE TABLE user_saved_post_places (user_saved_post_id BIGINT, place_id BIGINT)",
            "CREATE TABLE user_groups (id BIGINT, user_id BIGINT, color VARCHAR(30), deleted_at TIMESTAMP)",
            """
            CREATE TABLE group_posts (
                id BIGINT, group_id BIGINT, user_saved_post_id BIGINT, created_at TIMESTAMP, deleted_at TIMESTAMP
            )
            """,
            """
            CREATE TABLE group_share_links (
                id BIGINT, group_id BIGINT, token_value VARCHAR(100), revoked_at TIMESTAMP, expires_at TIMESTAMP
            )
            """,
            """
            CREATE TABLE shared_group_subscriptions (
                id BIGINT, member_id BIGINT, share_link_id BIGINT, created_at TIMESTAMP
            )
            """,
        )
        val fixtures = listOf(
            """
            INSERT INTO places (id, name, latitude, longitude) VALUES
                (1, 'own', 37.5, 127), (2, 'overlap', 37.5, 127),
                (3, 'shared', 37.5, 127), (4, 'ungrouped', 37.5, 127), (5, 'private', 37.5, 127)
            """,
            """
            INSERT INTO user_place_bookmarks VALUES
                (1, 7, 1, '2026-01-01'), (2, 7, 2, '2026-01-01'), (3, 7, 3, '2026-01-01'),
                (4, 7, 4, '2026-01-01'), (5, 7, 5, '2026-01-01'), (6, 8, 5, '2026-01-01')
            """,
            """
            INSERT INTO user_saved_posts VALUES
                (1, 7, '2026-01-01', NULL), (2, 7, '2026-01-01', NULL),
                (3, 8, '2026-01-01', NULL), (4, 7, '2026-01-01', NULL), (5, 9, '2026-01-01', NULL)
            """,
            "INSERT INTO user_saved_post_places VALUES (1, 1), (2, 2), (3, 2), (3, 3), (4, 4), (5, 5)",
            """
            INSERT INTO user_groups VALUES
                (1, 7, 'BLUE', NULL), (2, 8, 'RED', NULL), (3, 8, 'RED', NULL), (99, 9, 'RED', NULL)
            """,
            """
            INSERT INTO group_posts VALUES
                (1, 1, 1, '2026-01-01', NULL), (2, 1, 2, '2026-01-01', NULL),
                (3, 2, 3, '2026-01-01', NULL), (4, 99, 5, '2026-01-01', NULL),
                (5, 3, 3, '2026-01-01', NULL)
            """,
            """
            INSERT INTO group_share_links VALUES
                (1, 2, 'selected-token', NULL, NULL), (2, 3, 'other-token', NULL, NULL),
                (3, 99, 'private-token', NULL, NULL)
            """,
            """
            INSERT INTO shared_group_subscriptions VALUES
                (1, 7, 1, '2026-01-01'), (2, 7, 2, '2026-01-02'), (3, 8, 3, '2026-01-01')
            """,
        )
    }
}
