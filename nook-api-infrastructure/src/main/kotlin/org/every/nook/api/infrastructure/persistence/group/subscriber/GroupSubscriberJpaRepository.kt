package org.every.nook.api.infrastructure.persistence.group.subscriber

import org.every.nook.api.infrastructure.persistence.group.SharedGroupSubscriptionEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

private const val ACTIVE_SUBSCRIBERS = """
    FROM shared_group_subscriptions subscription
    INNER JOIN group_share_links link ON link.id = subscription.share_link_id
    INNER JOIN user_groups archive ON archive.id = link.group_id
    INNER JOIN members member ON member.id = subscription.member_id
    WHERE archive.deleted_at IS NULL
      AND link.revoked_at IS NULL
      AND (link.expires_at IS NULL OR link.expires_at > :now)
      AND member.status = 'ACTIVE'
      AND member.id <> archive.user_id
"""

interface GroupSubscriberJpaRepository : JpaRepository<SharedGroupSubscriptionEntity, Long> {
    @Query(
        value = """
            WITH subscribers AS (
                SELECT link.group_id AS group_id, member.id AS member_id,
                       member.nickname AS nickname, member.profile_image_url AS profile_image_url,
                       MAX(subscription.created_at) AS subscribed_at
                $ACTIVE_SUBSCRIBERS
                  AND link.group_id IN (:groupIds)
                GROUP BY link.group_id, member.id, member.nickname, member.profile_image_url
            ), ranked AS (
                SELECT subscribers.*,
                       COUNT(*) OVER (PARTITION BY group_id) AS subscriber_count,
                       ROW_NUMBER() OVER (
                           PARTITION BY group_id ORDER BY subscribed_at DESC, member_id DESC
                       ) AS position
                FROM subscribers
            )
            SELECT group_id AS groupId, nickname, profile_image_url AS profileImageUrl,
                   subscriber_count AS subscriberCount
            FROM ranked WHERE position <= 3
            ORDER BY group_id, position
        """,
        nativeQuery = true,
    )
    fun findSummaries(groupIds: Set<Long>, now: Instant): List<GroupSubscriberSummaryProjection>

    @Query(
        value = """
            SELECT member.nickname AS nickname, member.profile_image_url AS profileImageUrl
            $ACTIVE_SUBSCRIBERS
              AND link.group_id = :groupId
            GROUP BY member.id, member.nickname, member.profile_image_url
            ORDER BY MAX(subscription.created_at) DESC, member.id DESC
        """,
        countQuery = "SELECT COUNT(DISTINCT member.id) $ACTIVE_SUBSCRIBERS AND link.group_id = :groupId",
        nativeQuery = true,
    )
    fun findSubscribers(groupId: Long, now: Instant, pageable: Pageable): Page<GroupSubscriberProjection>
}

interface GroupSubscriberProjection {
    val nickname: String
    val profileImageUrl: String?
}

interface GroupSubscriberSummaryProjection : GroupSubscriberProjection {
    val groupId: Long
    val subscriberCount: Long
}
