package org.every.nook.api.infrastructure.persistence.place

import org.springframework.data.jpa.repository.Query
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.test.Test
import kotlin.test.assertTrue

class UserPlaceBookmarkJpaRepositoryRecentQueryTest {
    private val recentQuery = UserPlaceBookmarkJpaRepository::class.memberFunctions
        .single { it.name == "findRecentPlaces" }
        .findAnnotation<Query>()
        ?.value
        ?: error("findRecentPlaces must declare @Query")

    @Test
    fun `resolves a share token only for places outside my own saved posts`() {
        val shareTokenColumn = recentQuery.substringAfter("CASE").substringBefore("END AS shareToken")

        assertTrue(shareTokenColumn.contains("THEN NULL"))
        assertTrue(shareTokenColumn.contains("SELECT share_link.token_value"))
        assertTrue(shareTokenColumn.contains("uspp.place_id = p.id"))
    }

    @Test
    fun `picks a single deterministic share token for a place shared through several subscriptions`() {
        val shareTokenColumn = recentQuery.substringAfter("SELECT share_link.token_value")
            .substringBefore("END AS shareToken")

        assertTrue(shareTokenColumn.contains("ORDER BY subscription.created_at DESC, subscription.id DESC"))
        assertTrue(shareTokenColumn.contains("LIMIT 1"))
    }

    @Test
    fun `share token honours the same active share link rules as the access filter`() {
        val shareTokenSubquery = recentQuery.substringAfter("SELECT share_link.token_value")
            .substringBefore("ORDER BY subscription.created_at")
            .replace(Regex("\\s+"), " ")

        ACTIVE_SHARE_CONDITIONS.forEach { condition ->
            assertTrue(shareTokenSubquery.contains(condition), "share token subquery must keep: $condition")
        }
    }

    private companion object {
        val ACTIVE_SHARE_CONDITIONS = listOf(
            "subscription.member_id = upb.user_id",
            "shared_saved_post_place.place_id = p.id",
            "share_link.revoked_at IS NULL",
            "share_link.expires_at IS NULL OR share_link.expires_at > CURRENT_TIMESTAMP(6)",
            "shared_group_post.deleted_at IS NULL",
            "shared_group.deleted_at IS NULL",
            "shared_saved_post.deleted_at IS NULL",
        )
    }
}
