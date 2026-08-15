package org.every.nook.api.infrastructure.persistence.save

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserSavedPostPlaceMemoJpaRepository : JpaRepository<UserSavedPostPlaceMemoEntity, Long> {
    fun findByUserSavedPostIdAndPlaceId(userSavedPostId: Long, placeId: Long): UserSavedPostPlaceMemoEntity?

    fun findAllByUserSavedPostIdAndPlaceIdIn(
        userSavedPostId: Long,
        placeIds: Collection<Long>,
    ): List<UserSavedPostPlaceMemoEntity>

    fun findAllByPlaceIdAndUserSavedPostIdIn(
        placeId: Long,
        userSavedPostIds: Collection<Long>,
    ): List<UserSavedPostPlaceMemoEntity>

    @Modifying
    @Query(
        value = """
            INSERT IGNORE INTO user_saved_post_place_memos (
                user_id,
                user_saved_post_id,
                place_id,
                memo,
                created_at,
                updated_at
            )
            VALUES (
                :userId,
                :userSavedPostId,
                :placeId,
                :memo,
                CURRENT_TIMESTAMP(6),
                CURRENT_TIMESTAMP(6)
            )
        """,
        nativeQuery = true,
    )
    fun insertIgnore(
        @Param("userId") userId: Long,
        @Param("userSavedPostId") userSavedPostId: Long,
        @Param("placeId") placeId: Long,
        @Param("memo") memo: String,
    ): Int
}
