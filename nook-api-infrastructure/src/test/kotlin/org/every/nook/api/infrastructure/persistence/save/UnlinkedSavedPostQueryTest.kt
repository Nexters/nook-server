package org.every.nook.api.infrastructure.persistence.save

import org.springframework.data.jpa.repository.Query
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UnlinkedSavedPostQueryTest {
    private lateinit var connection: Connection
    private lateinit var jdbc: JdbcTemplate
    private lateinit var namedJdbc: NamedParameterJdbcTemplate

    @BeforeTest
    fun setUp() {
        val source = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL")
        connection = source.connection
        jdbc = JdbcTemplate(source)
        namedJdbc = NamedParameterJdbcTemplate(jdbc)
        schema.forEach(jdbc::execute)
        fixtures.forEach(jdbc::execute)
    }

    @AfterTest
    fun tearDown() {
        connection.close()
    }

    @Test
    fun `counts only own finished empty posts including failed place parsing and frozen copies`() {
        assertEquals(listOf(8L, 2L, 1L), ids())
        assertEquals(3L, count())
        // Multiple owned archives and another user's link to the same source must not change this count.
        assertEquals(listOf(8L, 2L), ids(size = 2))
        assertEquals(listOf(1L), ids(page = 1, size = 2))
        assertEquals(emptyList(), ids(page = 2, size = 2))
        assertEquals(3L, count())
    }

    @Test
    fun `connecting removing and deleting a post immediately changes both list and count`() {
        jdbc.execute("INSERT INTO user_saved_post_places VALUES (1, 900)")
        assertEquals(listOf(8L, 2L), ids())
        assertEquals(2L, count())
        jdbc.execute("DELETE FROM user_saved_post_places WHERE user_saved_post_id = 1")
        assertEquals(3L, count())
        jdbc.execute("UPDATE user_saved_posts SET deleted_at = CURRENT_TIMESTAMP WHERE id = 1")
        assertEquals(listOf(8L, 2L), ids())
        assertEquals(2L, count())
    }

    @Test
    fun `requires a live owned archive and respects membership deletion without losing other archives`() {
        jdbc.execute(
            "UPDATE group_posts SET deleted_at = CURRENT_TIMESTAMP WHERE group_id = 1 AND user_saved_post_id = 1",
        )
        assertEquals(3L, count())
        jdbc.execute("UPDATE user_groups SET deleted_at = CURRENT_TIMESTAMP WHERE id = 2")
        assertEquals(listOf(8L, 2L), ids())
        assertEquals(2L, count())
        jdbc.execute("UPDATE user_groups SET deleted_at = CURRENT_TIMESTAMP WHERE id = 1")
        assertEquals(emptyList(), ids())
        assertEquals(0L, count())
    }

    @Test
    fun `reprocessing hides mutable posts while independent frozen copies remain actionable`() {
        jdbc.execute("UPDATE place_parsing_jobs SET status = 'PROCESSING' WHERE post_id IN (101, 102)")
        assertEquals(listOf(8L), ids())
        assertEquals(1L, count())
        jdbc.execute("UPDATE post_content_parsing_jobs SET status = 'FAILED' WHERE post_id = 108")
        assertEquals(listOf(8L), ids())
    }

    private fun ids(page: Int = 0, size: Int = 20): List<Long> {
        val query = query("findUnlinked")
        return namedJdbc.query(
            query.value + " LIMIT :size OFFSET :offset",
            mapOf("userId" to 7L, "size" to size, "offset" to page * size),
        ) { row, _ -> row.getLong("id") }
    }

    private fun count(): Long = requireNotNull(
        namedJdbc.queryForObject(query("countUnlinked").value, mapOf("userId" to 7L), Long::class.java),
    )

    private fun query(name: String): Query = requireNotNull(
        UnlinkedSavedPostJpaRepository::class.java.methods.single { it.name == name }.getAnnotation(Query::class.java),
    )

    private companion object {
        val schema = listOf(
            "CREATE TABLE posts (id BIGINT PRIMARY KEY)",
            """CREATE TABLE user_saved_posts (id BIGINT PRIMARY KEY, user_id BIGINT, post_id BIGINT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, deleted_at TIMESTAMP,
                place_links_frozen BOOLEAN DEFAULT FALSE, UNIQUE(user_id, post_id))""",
            "CREATE TABLE group_posts (group_id BIGINT, user_saved_post_id BIGINT, deleted_at TIMESTAMP)",
            "CREATE TABLE user_groups (id BIGINT PRIMARY KEY, user_id BIGINT, deleted_at TIMESTAMP)",
            "CREATE TABLE user_saved_post_places (user_saved_post_id BIGINT, place_id BIGINT)",
            "CREATE TABLE post_content_parsing_jobs (post_id BIGINT PRIMARY KEY, status VARCHAR(20))",
            "CREATE TABLE place_parsing_jobs (post_id BIGINT PRIMARY KEY, status VARCHAR(20))",
        )
        val fixtures = listOf(
            """INSERT INTO posts VALUES
                (101),(102),(103),(104),(105),(106),(107),(108),(109),(110),(111),(112),(113),(114)""",
            """INSERT INTO user_saved_posts (id, user_id, post_id) VALUES
                (1,7,101),(2,7,102),(3,7,103),(4,7,104),(5,7,105),(6,7,106),(7,7,107),(8,7,108),
                (9,7,109),(10,7,110),(11,7,111),(12,7,112),(13,8,113),(14,7,114),(15,7,115),(16,8,101)""",
            "UPDATE user_saved_posts SET created_at = TIMESTAMP '2026-01-01'",
            "UPDATE user_saved_posts SET place_links_frozen = TRUE WHERE id = 8",
            "UPDATE user_saved_posts SET deleted_at = CURRENT_TIMESTAMP WHERE id = 9",
            "INSERT INTO user_groups VALUES (1,7,NULL),(2,7,NULL),(8,8,NULL),(10,7,CURRENT_TIMESTAMP)",
            """INSERT INTO group_posts (group_id, user_saved_post_id) VALUES
                (1,1),(2,1),(1,2),(1,3),(1,4),(1,5),(1,6),(1,7),(1,8),(1,9),(10,10),(1,11),
                (8,13),(8,14),(1,15),(8,16)""",
            "UPDATE group_posts SET deleted_at = CURRENT_TIMESTAMP WHERE user_saved_post_id = 11",
            "INSERT INTO user_saved_post_places VALUES (6, 900), (16, 900)",
            """INSERT INTO post_content_parsing_jobs VALUES
                (101,'COMPLETED'),(102,'COMPLETED'),(103,'COMPLETED'),(104,'PROCESSING'),(105,'FAILED'),
                (106,'COMPLETED'),(108,'PROCESSING'),(109,'COMPLETED'),(110,'COMPLETED'),(111,'COMPLETED'),
                (112,'COMPLETED'),(113,'COMPLETED'),(114,'COMPLETED')""",
            """INSERT INTO place_parsing_jobs VALUES
                (101,'COMPLETED'),(102,'FAILED'),(103,'PENDING'),(104,'COMPLETED'),(105,'FAILED'),
                (106,'COMPLETED'),(108,'PROCESSING'),(109,'COMPLETED'),(110,'COMPLETED'),(111,'COMPLETED'),
                (112,'COMPLETED'),(113,'COMPLETED'),(114,'COMPLETED')""",
        )
    }
}
