package org.every.nook.api.infrastructure.persistence.save

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

interface UnlinkedSavedPostJpaRepository : Repository<UserSavedPostEntity, Long> {
    @Query(
        value = "SELECT saved.* $UNLINKED_SAVED_POST_FILTER_SQL ORDER BY saved.created_at DESC, saved.id DESC",
        countQuery = "SELECT COUNT(*) $UNLINKED_SAVED_POST_FILTER_SQL",
        nativeQuery = true,
    )
    fun findUnlinked(@Param("userId") userId: Long, pageable: Pageable): Page<UserSavedPostEntity>

    @Query(value = "SELECT COUNT(*) $UNLINKED_SAVED_POST_FILTER_SQL", nativeQuery = true)
    fun countUnlinked(@Param("userId") userId: Long): Long
}

// EXISTS predicates keep one row per user/source post even when saved in multiple owned archives.
internal const val UNLINKED_SAVED_POST_FILTER_SQL = """
    FROM user_saved_posts saved
    INNER JOIN posts source_post ON source_post.id = saved.post_id
    WHERE saved.user_id = :userId
      AND saved.deleted_at IS NULL
      AND EXISTS (
          SELECT 1 FROM group_posts group_post
          INNER JOIN user_groups user_group ON user_group.id = group_post.group_id
          WHERE group_post.user_saved_post_id = saved.id
            AND group_post.deleted_at IS NULL
            AND user_group.deleted_at IS NULL
            AND user_group.user_id = saved.user_id
      )
      AND NOT EXISTS (
          SELECT 1 FROM user_saved_post_places place_link
          WHERE place_link.user_saved_post_id = saved.id
      )
      AND (
          saved.place_links_frozen = TRUE
          OR (
              NOT EXISTS (
                  SELECT 1 FROM post_content_parsing_jobs content_job
                  WHERE content_job.post_id = saved.post_id
                    AND content_job.status IN ('PENDING', 'PROCESSING', 'FAILED')
              )
              AND EXISTS (
                  SELECT 1 FROM place_parsing_jobs place_job
                  WHERE place_job.post_id = saved.post_id
                    AND place_job.status IN ('COMPLETED', 'FAILED')
              )
          )
      )
"""
