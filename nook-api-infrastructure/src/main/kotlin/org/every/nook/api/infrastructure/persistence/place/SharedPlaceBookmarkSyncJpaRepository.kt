package org.every.nook.api.infrastructure.persistence.place

import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

interface SharedPlaceBookmarkSyncJpaRepository : Repository<UserPlaceBookmarkEntity, Long> {
    @Modifying
    @Query(
        value = """
            INSERT IGNORE INTO user_place_bookmarks (user_id, place_id, created_at, updated_at)
            SELECT :memberId, saved_post_place.place_id, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
            FROM group_posts group_post
            INNER JOIN user_saved_posts saved_post ON saved_post.id = group_post.user_saved_post_id
            INNER JOIN user_saved_post_places saved_post_place
                ON saved_post_place.user_saved_post_id = saved_post.id
            WHERE group_post.group_id = :groupId
              AND group_post.deleted_at IS NULL
              AND saved_post.deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun insertAllFromSharedGroup(@Param("memberId") memberId: Long, @Param("groupId") groupId: Long): Int

    @Modifying
    @Query(
        value = """
            INSERT IGNORE INTO user_place_bookmarks (user_id, place_id, created_at, updated_at)
            SELECT subscription.member_id, :placeId, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
            FROM group_posts group_post
            INNER JOIN user_groups user_group ON user_group.id = group_post.group_id
            INNER JOIN group_share_links share_link ON share_link.group_id = group_post.group_id
            INNER JOIN shared_group_subscriptions subscription ON subscription.share_link_id = share_link.id
            WHERE group_post.user_saved_post_id = :savedPostId
              AND group_post.deleted_at IS NULL
              AND user_group.deleted_at IS NULL
              AND share_link.revoked_at IS NULL
              AND (share_link.expires_at IS NULL OR share_link.expires_at > CURRENT_TIMESTAMP(6))
        """,
        nativeQuery = true,
    )
    fun insertForActiveSubscribers(@Param("savedPostId") savedPostId: Long, @Param("placeId") placeId: Long): Int

    @Modifying
    @Query(
        value = """
            INSERT IGNORE INTO user_place_bookmarks (user_id, place_id, created_at, updated_at)
            SELECT subscription.member_id, saved_post_place.place_id, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
            FROM group_posts group_post
            INNER JOIN user_groups user_group ON user_group.id = group_post.group_id
            INNER JOIN group_share_links share_link ON share_link.group_id = group_post.group_id
            INNER JOIN shared_group_subscriptions subscription ON subscription.share_link_id = share_link.id
            INNER JOIN user_saved_post_places saved_post_place
                ON saved_post_place.user_saved_post_id = group_post.user_saved_post_id
            WHERE group_post.user_saved_post_id = :savedPostId
              AND group_post.group_id IN (:groupIds)
              AND group_post.deleted_at IS NULL
              AND user_group.deleted_at IS NULL
              AND share_link.revoked_at IS NULL
              AND (share_link.expires_at IS NULL OR share_link.expires_at > CURRENT_TIMESTAMP(6))
        """,
        nativeQuery = true,
    )
    fun insertAllForActiveSubscribers(
        @Param("savedPostId") savedPostId: Long,
        @Param("groupIds") groupIds: Collection<Long>,
    ): Int
}
