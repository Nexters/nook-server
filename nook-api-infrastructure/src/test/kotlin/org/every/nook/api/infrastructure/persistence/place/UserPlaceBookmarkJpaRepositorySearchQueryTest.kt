package org.every.nook.api.infrastructure.persistence.place

import org.springframework.data.jpa.repository.Query
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserPlaceBookmarkJpaRepositorySearchQueryTest {
    private val searchQuery = UserPlaceBookmarkJpaRepository::class.memberFunctions
        .single { it.name == "searchSavedPlaces" }
        .findAnnotation<Query>()
        ?: error("searchSavedPlaces must declare @Query")

    @Test
    fun `search and count allow places from active shared subscriptions without a group filter`() {
        listOf(searchQuery.value, searchQuery.countQuery).forEach { sql ->
            assertTrue(sql.contains(":groupId IS NULL"))
            assertTrue(sql.contains("FROM shared_group_subscriptions subscription"))
            assertTrue(sql.contains("subscription.member_id = upb.user_id"))
            assertTrue(sql.contains("shared_saved_post_place.place_id = p.id"))
            assertTrue(sql.contains("share_link.revoked_at IS NULL"))
            assertTrue(sql.contains("share_link.expires_at > CURRENT_TIMESTAMP(6)"))
            assertTrue(sql.contains("shared_group_post.deleted_at IS NULL"))
            assertTrue(sql.contains("shared_group.deleted_at IS NULL"))
            assertTrue(sql.contains("shared_saved_post.deleted_at IS NULL"))
        }
    }

    @Test
    fun `search and count use the same shared access predicate`() {
        assertEquals(
            searchQuery.value.sharedAccessPredicate(),
            searchQuery.countQuery.sharedAccessPredicate(),
        )
    }

    private fun String.sharedAccessPredicate(): String = substringAfter(
        "OR (\n                      :groupId IS NULL",
    )
        .substringBefore("\n                  )\n              )")
        .replace(Regex("\\s+"), " ")
        .trim()
}
