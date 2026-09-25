package org.every.nook.api.infrastructure.persistence.admin

import org.every.nook.api.infrastructure.persistence.place.PlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostPlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceJpaRepository
import org.springframework.data.jpa.repository.Query
import kotlin.test.Test
import kotlin.test.assertContains

class AdminPlaceQueryPolicyTest {
    @Test
    fun `admin place page applies filtering sorting and pagination in database`() {
        val query = PlaceJpaRepository::class.java
            .getMethod("findAdminPage", String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .getAnnotation(Query::class.java)
            .value

        assertContains(query, "LOWER(place.name)")
        assertContains(query, "LOWER(place.address)")
        assertContains(query, "LOWER(place.external_place_id)")
        assertContains(query, "ORDER BY place.created_at DESC, place.id DESC")
        assertContains(query, "LIMIT :limit OFFSET :offset")
    }

    @Test
    fun `admin place impact counts are aggregated for all page ids`() {
        val postCountQuery = PostPlaceJpaRepository::class.java
            .getMethod("countDistinctPostsByPlaceIdIn", Collection::class.java)
            .getAnnotation(Query::class.java)
            .value
        val userCountQuery = UserSavedPostPlaceJpaRepository::class.java
            .getMethod("countDistinctActiveUsersByPlaceIdIn", Collection::class.java)
            .getAnnotation(Query::class.java)
            .value

        assertContains(postCountQuery, "mapping.placeId IN :placeIds")
        assertContains(postCountQuery, "GROUP BY mapping.placeId")
        assertContains(userCountQuery, "saved_post_place.place_id IN (:placeIds)")
        assertContains(userCountQuery, "GROUP BY saved_post_place.place_id")
    }
}
