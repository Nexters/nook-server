package org.every.nook.api.application.group.port

import org.every.nook.api.application.group.GroupShareLinkView
import org.every.nook.api.application.group.SharedGroupAccess
import org.every.nook.api.application.group.SharedGroupView
import java.time.Instant

interface GroupSharePort {
    fun issue(ownerId: Long, groupId: Long, expiresAt: Instant?): GroupShareLinkView?

    fun revoke(ownerId: Long, groupId: Long): Boolean

    fun resolve(token: String): ResolveResult

    fun findGroup(access: SharedGroupAccess): SharedGroupView?

    fun findSubscribedGroups(memberId: Long): List<org.every.nook.api.application.group.GroupView>

    fun subscribe(memberId: Long, access: SharedGroupAccess): Boolean

    fun unsubscribe(memberId: Long, groupId: Long): Boolean

    fun resolveMemberAccess(memberId: Long, groupId: Long): SharedGroupAccess?

    fun containsPost(access: SharedGroupAccess, savedPostId: Long): Boolean

    fun containsPlace(access: SharedGroupAccess, placeId: Long): Boolean

    sealed interface ResolveResult {
        data class Active(val access: SharedGroupAccess) : ResolveResult
        data object NotFound : ResolveResult
        data object Revoked : ResolveResult
        data object Expired : ResolveResult
        data object GroupUnavailable : ResolveResult
    }
}
