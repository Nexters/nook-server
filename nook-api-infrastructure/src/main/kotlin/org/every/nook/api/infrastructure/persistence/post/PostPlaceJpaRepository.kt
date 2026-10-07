package org.every.nook.api.infrastructure.persistence.post

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PostPlaceJpaRepository : JpaRepository<PostPlaceEntity, Long> {
    interface PlacePostCount {
        val placeId: Long
        val itemCount: Long
    }

    fun findByPostIdAndPlaceId(postId: Long, placeId: Long): PostPlaceEntity?

    fun findAllByPostIdOrderBySequenceAsc(postId: Long): List<PostPlaceEntity>

    fun findAllByPostIdInOrderByPostIdAscSequenceAsc(postIds: Collection<Long>): List<PostPlaceEntity>

    fun findAllByPlaceId(placeId: Long): List<PostPlaceEntity>

    @Query(
        """
        SELECT mapping.placeId AS placeId, COUNT(DISTINCT mapping.postId) AS itemCount
        FROM PostPlaceEntity mapping
        WHERE mapping.placeId IN :placeIds
        GROUP BY mapping.placeId
        """,
    )
    fun countDistinctPostsByPlaceIdIn(@Param("placeIds") placeIds: Collection<Long>): List<PlacePostCount>
}
