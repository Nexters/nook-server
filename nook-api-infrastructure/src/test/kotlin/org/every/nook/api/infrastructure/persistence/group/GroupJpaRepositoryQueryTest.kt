package org.every.nook.api.infrastructure.persistence.group

import org.springframework.data.jpa.repository.Query
import kotlin.test.Test
import kotlin.test.assertContains

class GroupJpaRepositoryQueryTest {
    @Test
    fun `group thumbnails use video thumbnails as representative post media`() {
        val query = GroupJpaRepository::class.java
            .getMethod("findRecentThumbnailUrls", Long::class.javaPrimitiveType)
            .getAnnotation(Query::class.java)
            .value

        assertContains(query, "WHEN post_media.media_type = 'IMAGE' THEN post_media.media_url")
        assertContains(query, "WHEN post_media.media_type = 'VIDEO' THEN post_media.thumbnail_url")
        assertContains(query, "post_media.media_type = 'VIDEO' AND post_media.thumbnail_url IS NOT NULL")
    }

    @Test
    fun `groups are ordered by their most recent saved post then creation time`() {
        val query = GroupJpaRepository::class.java
            .getMethod("findAllSummaries", Long::class.javaPrimitiveType)
            .getAnnotation(Query::class.java)
            .value

        assertContains(query, "MAX(CASE WHEN saved_post.id IS NOT NULL THEN group_post.updated_at END) AS lastSavedAt")
        assertContains(query, "MAX(CASE WHEN saved_post.id IS NOT NULL THEN group_post.updated_at END) IS NULL ASC")
        assertContains(query, "MAX(CASE WHEN saved_post.id IS NOT NULL THEN group_post.updated_at END) DESC")
        assertContains(query, "user_group.created_at DESC")
    }
}
