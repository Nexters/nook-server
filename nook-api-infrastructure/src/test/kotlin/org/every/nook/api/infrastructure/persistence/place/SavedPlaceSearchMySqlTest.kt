package org.every.nook.api.infrastructure.persistence.place

import org.springframework.data.jpa.repository.Query
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.MySQLContainer
import kotlin.test.Test
import kotlin.test.assertEquals

class SavedPlaceSearchMySqlTest {
    @Test
    fun `MySQL search matches own memo and visible tags while preserving counts access and pagination`() {
        MySQLContainer("mysql:8.4").use { mysql ->
            mysql.start()
            val dataSource = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
            val jdbc = JdbcTemplate(dataSource)
            createSchema(jdbc)
            seed(jdbc)
            val search = SearchQueries(NamedParameterJdbcTemplate(dataSource))
            verifyMemoAndTags(search)
            verifyExistingSearchAndGroups(search)
            verifySharedAccess(jdbc, search)
        }
    }

    private fun verifyMemoAndTags(search: SearchQueries) {
        // Both matching tags and duplicate saved posts must still yield one place.
        assertEquals(listOf(1L, 2L), search.ids("조용"))
        assertEquals(2L, search.count("조용"))
        assertEquals(mapOf(1L to 2L, 2L to 1L), search.groups("조용"))
        assertEquals(listOf(1L), search.ids("조용", page = 0, size = 1))
        assertEquals(listOf(2L), search.ids("조용", page = 1, size = 1))
        assertEquals(emptyList(), search.ids("조용", page = 2, size = 1))
        listOf("타인메모", "비활성", "QUIET", "추론단어", "미표시").forEach { keyword ->
            assertEquals(emptyList(), search.ids(keyword), keyword)
            assertEquals(0L, search.count(keyword), keyword)
            assertEquals(emptyMap(), search.groups(keyword), keyword)
        }
        assertEquals(listOf(1L), search.ids("100%_!"))
        assertEquals(1L, search.count("100%_!"))
        assertEquals(mapOf(1L to 1L), search.groups("100%_!"))
    }

    private fun verifyExistingSearchAndGroups(search: SearchQueries) {
        assertEquals(listOf(2L), search.ids("Beta"))
        assertEquals(listOf(2L), search.ids("부산"))
        assertEquals(listOf(2L), search.ids("식당"))
        // Existing name matches precede address matches, then memo/tag/category matches.
        assertEquals(listOf(3L, 4L, 1L, 2L), search.ids("정렬"))
        assertEquals(4L, search.count("정렬"))
        assertEquals(listOf(1L, 2L), search.ids("조용", groupId = 1))
        assertEquals(listOf(2L), search.ids("조용", groupId = 2))
        assertEquals(1L, search.count("조용", groupId = 2))
        assertEquals(emptyList(), search.ids("조용", groupId = 3))
        assertEquals(0L, search.count("조용", groupId = 3))
    }

    private fun verifySharedAccess(jdbc: JdbcTemplate, search: SearchQueries) {
        assertEquals(listOf(5L), search.ids("공유태그"))
        assertEquals(1L, search.count("공유태그"))
        assertEquals(emptyMap(), search.groups("공유태그"))
        assertEquals(emptyList(), search.ids("공유태그", groupId = 1))
        assertEquals(emptyList(), search.ids("공유자메모"))
        assertEquals(listOf(5L), search.ids("내공유메모"))
        jdbc.update("UPDATE group_share_links SET revoked_at = CURRENT_TIMESTAMP(6)")
        assertEquals(emptyList(), search.ids("공유태그"))
        assertEquals(0L, search.count("공유태그"))
    }

    private fun createSchema(jdbc: JdbcTemplate) {
        val tables = listOf(
            """places (id BIGINT PRIMARY KEY, name VARCHAR(50), address VARCHAR(50), category VARCHAR(50),
                provider_category VARCHAR(50), thumbnail_url VARCHAR(255), representative_tags JSON)""",
            "user_place_bookmarks (user_id BIGINT, place_id BIGINT, memo TEXT)",
            """user_saved_posts (id BIGINT PRIMARY KEY, user_id BIGINT, post_id BIGINT,
                created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6), deleted_at TIMESTAMP(6) NULL)""",
            "user_saved_post_places (user_saved_post_id BIGINT, place_id BIGINT)",
            """user_groups (id BIGINT PRIMARY KEY, user_id BIGINT, name VARCHAR(50), color VARCHAR(20),
                deleted_at TIMESTAMP(6) NULL)""",
            "group_posts (group_id BIGINT, user_saved_post_id BIGINT, deleted_at TIMESTAMP(6) NULL)",
            "post_media (post_id BIGINT, media_url VARCHAR(255), media_type VARCHAR(20), display_order INT)",
            "shared_group_subscriptions (member_id BIGINT, share_link_id BIGINT)",
            """group_share_links (id BIGINT PRIMARY KEY, group_id BIGINT,
                revoked_at TIMESTAMP(6) NULL, expires_at TIMESTAMP(6) NULL)""",
            """place_tag_catalogs (tag_code VARCHAR(50), display_name VARCHAR(50), enabled BOOLEAN,
                matching_keywords JSON)""",
        )
        tables.forEach { jdbc.execute("CREATE TABLE $it") }
    }

    private fun seed(jdbc: JdbcTemplate) {
        jdbc.update(
            """
            INSERT INTO places (id, name, address, category, representative_tags) VALUES
                (1, 'Alpha', '서울', '카페', '["QUIET", "QUIET_SECOND", "DISABLED", "SORT"]'),
                (2, 'Beta', '부산', '식당', '[]'),
                (3, '정렬 이름', '서울', NULL, '[]'),
                (4, '가나다', '정렬 주소', NULL, '[]'),
                (5, '공유장소', '서울', NULL, '["SHARED"]'),
                (6, '삭제장소', '서울', NULL, '["QUIET"]')
            """.trimIndent(),
        )
        jdbc.update(
            """
            INSERT INTO place_tag_catalogs VALUES
                ('QUIET', '조용한 공간', TRUE, '["추론단어"]'),
                ('QUIET_SECOND', '조용한 분위기', TRUE, '[]'),
                ('DISABLED', '비활성', FALSE, '[]'),
                ('SORT', '정렬 태그', TRUE, '[]'),
                ('SHARED', '공유태그', TRUE, '[]'),
                ('HIDDEN', '미표시', TRUE, '[]')
            """.trimIndent(),
        )
        jdbc.update(
            """
            INSERT INTO user_place_bookmarks VALUES
                (1, 1, '100%_!'), (1, 2, '조용하고 정렬하기 좋은 곳'), (1, 3, NULL), (1, 4, NULL),
                (2, 1, '타인메모'), (1, 5, '내공유메모'), (2, 5, '공유자메모'), (1, 6, '조용')
            """.trimIndent(),
        )
        jdbc.update(
            """
            INSERT INTO user_saved_posts (id, user_id, post_id, deleted_at) VALUES
                (1, 1, 1, NULL), (2, 1, 2, NULL), (3, 1, 3, NULL), (4, 1, 4, NULL),
                (5, 1, 5, NULL), (6, 2, 6, NULL), (7, 1, 7, CURRENT_TIMESTAMP(6))
            """.trimIndent(),
        )
        jdbc.update(
            "INSERT INTO user_saved_post_places VALUES (1, 1), (2, 2), (3, 1), (4, 3), (5, 4), (6, 5), (7, 6)",
        )
        jdbc.update("INSERT INTO user_groups VALUES (1, 1, '첫째', 'MINT', NULL), (2, 1, '둘째', 'MINT', NULL)")
        jdbc.update("INSERT INTO user_groups VALUES (3, 2, '타인', 'MINT', NULL)")
        jdbc.update(
            "INSERT INTO group_posts VALUES (1, 1, NULL), (1, 2, NULL), (1, 3, NULL), (2, 2, NULL), (3, 6, NULL)",
        )
        jdbc.update("INSERT INTO group_share_links VALUES (1, 3, NULL, NULL)")
        jdbc.update("INSERT INTO shared_group_subscriptions VALUES (1, 1)")
    }
}

private class SearchQueries(private val jdbc: NamedParameterJdbcTemplate) {
    private val search = query("searchSavedPlaces")
    private val groups = query("findSavedPlaceSearchGroups")

    fun ids(keyword: String, groupId: Long? = null, page: Int = 0, size: Int = 20): List<Long> = jdbc.query(
        search.value + " LIMIT :limit OFFSET :offset",
        parameters(keyword, groupId) + mapOf("limit" to size, "offset" to page * size),
    ) { row, _ -> row.getLong("id") }

    fun count(keyword: String, groupId: Long? = null): Long = requireNotNull(
        jdbc.queryForObject(search.countQuery, parameters(keyword, groupId), Long::class.java),
    )

    fun groups(keyword: String): Map<Long, Long> = jdbc.query(
        groups.value,
        parameters(keyword, null),
    ) { row, _ -> row.getLong("id") to row.getLong("matchedPlaceCount") }.toMap()

    private fun parameters(keyword: String, groupId: Long?): Map<String, Any?> = mapOf(
        "userId" to 1L,
        "groupId" to groupId,
        "pattern" to "%${keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_")}%",
    )

    private fun query(name: String): Query = requireNotNull(
        UserPlaceBookmarkJpaRepository::class.java.methods.single { it.name == name }.getAnnotation(Query::class.java),
    )
}
