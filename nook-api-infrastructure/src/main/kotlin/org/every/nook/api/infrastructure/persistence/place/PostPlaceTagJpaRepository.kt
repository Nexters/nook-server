package org.every.nook.api.infrastructure.persistence.place

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PostPlaceTagJpaRepository : JpaRepository<PostPlaceTagEntity, Long> {
    fun deleteAllByPostIdAndPlaceId(postId: Long, placeId: Long)

    @Query(
        """
        SELECT tag
        FROM PostPlaceTagEntity
        WHERE placeId = :placeId
        GROUP BY tag
        ORDER BY COUNT(id) DESC, AVG(confidence) DESC, tag ASC
        """,
    )
    fun findRepresentativeTags(@Param("placeId") placeId: Long): List<String>

    @Query(
        value = """
            WITH relations AS (
                SELECT post_id, place_id FROM post_places
                UNION
                SELECT saved.post_id, mapping.place_id
                FROM user_saved_post_places mapping
                INNER JOIN user_saved_posts saved ON saved.id = mapping.user_saved_post_id
                WHERE saved.deleted_at IS NULL
            )
            SELECT relation.post_id AS postId, relation.place_id AS placeId
            FROM relations relation
            WHERE relation.post_id IN (SELECT post_id FROM relations WHERE place_id = :placeId)
            ORDER BY relation.post_id, relation.place_id
        """,
        nativeQuery = true,
    )
    fun findReprocessingContexts(@Param("placeId") placeId: Long): List<PlaceTagBackfillProjection>

    fun findAllByTag(tag: String): List<PostPlaceTagEntity>

    @Query(
        value = """
            SELECT
                relation.post_id AS postId,
                relation.place_id AS placeId
            FROM (
                SELECT
                    post_place.post_id,
                    post_place.place_id
                FROM post_places post_place

                UNION ALL

                SELECT
                    saved_post.post_id,
                    saved_post_place.place_id
                FROM user_saved_post_places saved_post_place
                INNER JOIN user_saved_posts saved_post
                    ON saved_post.id = saved_post_place.user_saved_post_id
                WHERE saved_post.deleted_at IS NULL
            ) relation
            GROUP BY relation.post_id, relation.place_id
            ORDER BY relation.post_id, relation.place_id
        """,
        nativeQuery = true,
    )
    fun findAllBackfillTargets(): List<PlaceTagBackfillProjection>
}

interface PlaceTagBackfillProjection {
    val postId: Long
    val placeId: Long
}
