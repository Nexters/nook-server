package org.every.nook.api.infrastructure.persistence.group

import org.every.nook.api.application.group.GroupAccessType
import org.every.nook.api.application.group.GroupOwnerView
import org.every.nook.api.application.group.GroupShareLinkView
import org.every.nook.api.application.group.GroupView
import org.every.nook.api.application.group.SharedGroupAccess
import org.every.nook.api.application.group.SharedGroupView
import org.every.nook.api.application.group.port.GroupReadAccessPort
import org.every.nook.api.application.group.port.GroupSharePort
import org.every.nook.api.infrastructure.persistence.member.MemberJpaRepository
import org.every.nook.api.infrastructure.persistence.place.SharedPlaceBookmarkSyncJpaRepository
import org.every.nook.api.infrastructure.persistence.save.SharedGroupContentJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Component
class GroupSharePersistenceAdapter(
    private val shareLinkRepository: GroupShareLinkJpaRepository,
    private val subscriptionRepository: SharedGroupSubscriptionJpaRepository,
    private val groupRepository: GroupJpaRepository,
    private val groupPostRepository: GroupPostJpaRepository,
    private val savedPostRepository: UserSavedPostJpaRepository,
    private val sharedContentRepository: SharedGroupContentJpaRepository,
    private val memberRepository: MemberJpaRepository,
    private val bookmarkRepository: SharedPlaceBookmarkSyncJpaRepository,
    private val clock: Clock = Clock.systemUTC(),
) : GroupSharePort,
    GroupReadAccessPort {
    @Transactional
    override fun issue(ownerId: Long, groupId: Long, expiresAt: Instant?): GroupShareLinkView? {
        if (groupRepository.findByIdAndUserIdForUpdate(groupId, ownerId) == null) return null
        val now = clock.instant()
        val active = shareLinkRepository.findFirstByGroupIdAndRevokedAtIsNullOrderByIdDesc(groupId)
            ?.takeUnless { it.expiresAt?.let { expiry -> !expiry.isAfter(now) } ?: false }
        if (active != null) return GroupShareLinkView(active.tokenValue, active.expiresAt)

        val token = ShareTokenCodec.generate()
        val saved = shareLinkRepository.saveAndFlush(
            GroupShareLinkEntity(groupId, ShareTokenCodec.hash(token), token, expiresAt),
        )
        return GroupShareLinkView(saved.tokenValue, saved.expiresAt)
    }

    @Transactional
    override fun revoke(ownerId: Long, groupId: Long): Boolean {
        if (groupRepository.findByIdAndUserIdForUpdate(groupId, ownerId) == null) return false
        shareLinkRepository.findFirstByGroupIdAndRevokedAtIsNullOrderByIdDesc(groupId)?.revoke(clock.instant())
        return true
    }

    @Transactional(readOnly = true)
    override fun resolve(token: String): GroupSharePort.ResolveResult {
        val link = shareLinkRepository.findByTokenHash(ShareTokenCodec.hash(token))
        return when {
            link == null || link.tokenValue != token -> GroupSharePort.ResolveResult.NotFound

            link.revokedAt != null -> GroupSharePort.ResolveResult.Revoked

            link.expiresAt?.isAfter(clock.instant()) == false -> GroupSharePort.ResolveResult.Expired

            else -> groupRepository.findById(link.groupId).orElse(null)?.let { group ->
                GroupSharePort.ResolveResult.Active(
                    SharedGroupAccess(
                        shareLinkId = requireNotNull(link.id),
                        groupId = requireNotNull(group.id),
                        ownerId = group.userId,
                        token = link.tokenValue,
                    ),
                )
            } ?: GroupSharePort.ResolveResult.GroupUnavailable
        }
    }

    @Transactional(readOnly = true)
    override fun findGroup(access: SharedGroupAccess): SharedGroupView? {
        val group = groupRepository.findById(access.groupId).orElse(null) ?: return null
        val member = memberRepository.findById(access.ownerId).orElse(null) ?: return null
        val thumbnails = groupRepository.findRecentThumbnailUrls(access.ownerId)
            .filter { it.groupId == access.groupId }
            .mapNotNull { it.postMediaUrl ?: it.placeThumbnailUrl }
        val owner = GroupOwnerView(member.nickname, member.profileImageUrl)
        return SharedGroupView(
            group = GroupView(
                id = access.groupId,
                name = group.name,
                color = group.color.name,
                postCount = groupPostRepository.countByGroupId(access.groupId),
                thumbnailUrls = thumbnails,
                accessType = GroupAccessType.SHARED,
                owner = owner,
                shareToken = access.token,
            ),
            owner = owner,
        )
    }

    @Transactional(readOnly = true)
    override fun findSubscribedGroups(memberId: Long): List<GroupView> =
        subscriptionRepository.findAllByMemberId(memberId).mapNotNull { subscription ->
            val link = shareLinkRepository.findById(subscription.shareLinkId).orElse(null) ?: return@mapNotNull null
            val result = resolve(link.tokenValue)
            val access = (result as? GroupSharePort.ResolveResult.Active)?.access ?: return@mapNotNull null
            findGroup(access)?.group
        }

    @Transactional
    override fun subscribe(memberId: Long, access: SharedGroupAccess): Boolean {
        if (subscriptionRepository.existsByMemberIdAndShareLinkId(memberId, access.shareLinkId)) return false
        subscriptionRepository.save(SharedGroupSubscriptionEntity(memberId, access.shareLinkId))
        bookmarkRepository.insertAllFromSharedGroup(memberId, access.groupId)
        return true
    }

    @Transactional
    override fun unsubscribe(memberId: Long, groupId: Long): Boolean =
        subscriptionRepository.deleteByMemberIdAndGroupId(memberId, groupId) > 0

    @Transactional(readOnly = true)
    override fun resolveMemberAccess(memberId: Long, groupId: Long): SharedGroupAccess? {
        val subscription = subscriptionRepository.findByMemberIdAndGroupId(memberId, groupId) ?: return null
        val link = shareLinkRepository.findById(subscription.shareLinkId).orElse(null) ?: return null
        return (resolve(link.tokenValue) as? GroupSharePort.ResolveResult.Active)?.access
    }

    @Transactional(readOnly = true)
    override fun resolveOwnerId(memberId: Long, groupId: Long): Long? =
        groupRepository.findByIdAndUserId(groupId, memberId)?.userId ?: resolveMemberAccess(memberId, groupId)?.ownerId

    @Transactional(readOnly = true)
    override fun containsPost(access: SharedGroupAccess, savedPostId: Long): Boolean =
        savedPostRepository.findByIdAndUserId(savedPostId, access.ownerId) != null &&
            groupPostRepository.existsByGroupIdAndUserSavedPostId(access.groupId, savedPostId)

    @Transactional(readOnly = true)
    override fun containsPlace(access: SharedGroupAccess, placeId: Long): Boolean =
        sharedContentRepository.existsPlaceInGroup(access.ownerId, access.groupId, placeId) > 0
}
