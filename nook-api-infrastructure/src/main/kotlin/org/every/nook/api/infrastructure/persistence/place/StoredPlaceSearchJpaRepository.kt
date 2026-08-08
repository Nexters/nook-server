package org.every.nook.api.infrastructure.persistence.place

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.math.BigDecimal

interface StoredPlaceSearchJpaRepository : Repository<PlaceEntity, Long> {
    @Query(
        value = """
            SELECT
                p.id AS id,
                p.name AS name,
                p.address AS address,
                p.category AS category,
                p.latitude AS latitude,
                p.longitude AS longitude,
                p.thumbnail_url AS thumbnailUrl,
                CAST(p.representative_tags AS CHAR) AS representativeTags,
                EXISTS (
                    SELECT 1
                    FROM user_place_bookmarks upb
                    WHERE upb.user_id = :userId
                      AND upb.place_id = p.id
                      AND EXISTS (
                          SELECT 1
                          FROM user_saved_posts usp
                          INNER JOIN post_places pp ON pp.post_id = usp.post_id
                          WHERE usp.user_id = upb.user_id
                            AND usp.deleted_at IS NULL
                            AND pp.place_id = p.id
                      )
                ) AS bookmarked
            FROM places p
            WHERE (p.name LIKE CONCAT('%', :keyword, '%') OR p.address LIKE CONCAT('%', :keyword, '%'))
            ORDER BY p.name ASC, p.id ASC
            LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true,
    )
    fun searchAll(
        @Param("userId") userId: Long,
        @Param("keyword") keyword: String,
        @Param("offset") offset: Int,
        @Param("limit") limit: Int,
    ): List<StoredPlaceSearchProjection>

    @Query(
        value = """
            SELECT
                p.id AS id,
                p.name AS name,
                p.address AS address,
                p.category AS category,
                p.latitude AS latitude,
                p.longitude AS longitude,
                p.thumbnail_url AS thumbnailUrl,
                CAST(p.representative_tags AS CHAR) AS representativeTags,
                TRUE AS bookmarked
            FROM places p
            WHERE (p.name LIKE CONCAT('%', :keyword, '%') OR p.address LIKE CONCAT('%', :keyword, '%'))
              AND EXISTS (
                  SELECT 1
                  FROM user_place_bookmarks upb
                  WHERE upb.user_id = :userId
                    AND upb.place_id = p.id
                    AND EXISTS (
                        SELECT 1
                        FROM user_saved_posts usp
                        INNER JOIN post_places pp ON pp.post_id = usp.post_id
                        WHERE usp.user_id = upb.user_id
                          AND usp.deleted_at IS NULL
                          AND pp.place_id = p.id
                    )
              )
            ORDER BY p.name ASC, p.id ASC
            LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true,
    )
    fun searchMine(
        @Param("userId") userId: Long,
        @Param("keyword") keyword: String,
        @Param("offset") offset: Int,
        @Param("limit") limit: Int,
    ): List<StoredPlaceSearchProjection>
}

interface StoredPlaceSearchProjection {
    val id: Long
    val name: String
    val address: String
    val category: String?
    val latitude: BigDecimal
    val longitude: BigDecimal
    val thumbnailUrl: String?
    val representativeTags: String?
    val bookmarked: Boolean
}
