package org.every.nook.api.infrastructure.persistence.group

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GroupShareLinkJpaRepository : JpaRepository<GroupShareLinkEntity, Long> {
    fun findFirstByGroupIdAndRevokedAtIsNullOrderByIdDesc(groupId: Long): GroupShareLinkEntity?

    fun findByTokenHash(tokenHash: String): GroupShareLinkEntity?
}

interface SharedGroupSubscriptionJpaRepository : JpaRepository<SharedGroupSubscriptionEntity, Long> {
    fun existsByMemberIdAndShareLinkId(memberId: Long, shareLinkId: Long): Boolean

    fun findAllByMemberId(memberId: Long): List<SharedGroupSubscriptionEntity>

    @Query(
        value = """
            SELECT subscription.*
            FROM shared_group_subscriptions subscription
            INNER JOIN group_share_links share_link ON share_link.id = subscription.share_link_id
            WHERE subscription.member_id = :memberId
              AND share_link.group_id = :groupId
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findByMemberIdAndGroupId(
        @Param("memberId") memberId: Long,
        @Param("groupId") groupId: Long,
    ): SharedGroupSubscriptionEntity?

    @Query(
        value = """
            DELETE subscription
            FROM shared_group_subscriptions subscription
            INNER JOIN group_share_links share_link ON share_link.id = subscription.share_link_id
            WHERE subscription.member_id = :memberId
              AND share_link.group_id = :groupId
        """,
        nativeQuery = true,
    )
    @org.springframework.data.jpa.repository.Modifying
    fun deleteByMemberIdAndGroupId(@Param("memberId") memberId: Long, @Param("groupId") groupId: Long): Int
}
