package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.domain.place.PlaceTag
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
    fun findRepresentativeTags(@Param("placeId") placeId: Long): List<PlaceTag>
}
