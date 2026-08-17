package org.every.nook.api.application.group

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.error.ShareLinkExpiredException
import org.every.nook.api.application.group.error.ShareLinkNotFoundException
import org.every.nook.api.application.group.error.ShareLinkRevokedException
import org.every.nook.api.application.group.error.SharedGroupUnavailableException
import org.every.nook.api.application.group.port.GroupSharePort
import java.time.Instant

class IssueGroupShareLinkUseCase(private val port: GroupSharePort) {
    operator fun invoke(command: Command): GroupShareLinkView =
        port.issue(command.ownerId, command.groupId, command.expiresAt) ?: throw GroupNotFoundException()

    data class Command(val ownerId: Long, val groupId: Long, val expiresAt: Instant? = null)
}

class RevokeGroupShareLinkUseCase(private val port: GroupSharePort) {
    operator fun invoke(ownerId: Long, groupId: Long) {
        if (!port.revoke(ownerId, groupId)) throw GroupNotFoundException()
    }
}

class GetSharedGroupUseCase(private val port: GroupSharePort) {
    operator fun invoke(token: String): SharedGroupView {
        val access = port.resolveActive(token)
        return port.findGroup(access) ?: throw SharedGroupUnavailableException()
    }
}

class SubscribeSharedGroupUseCase(private val port: GroupSharePort) {
    operator fun invoke(memberId: Long, token: String) {
        val access = port.resolveActive(token)
        if (access.ownerId == memberId) return
        port.subscribe(memberId, access)
    }
}

class UnsubscribeSharedGroupUseCase(private val port: GroupSharePort) {
    operator fun invoke(memberId: Long, groupId: Long) {
        port.unsubscribe(memberId, groupId)
    }
}

fun GroupSharePort.resolveActive(token: String): SharedGroupAccess = when (val result = resolve(token)) {
    is GroupSharePort.ResolveResult.Active -> result.access
    GroupSharePort.ResolveResult.NotFound -> throw ShareLinkNotFoundException()
    GroupSharePort.ResolveResult.Revoked -> throw ShareLinkRevokedException()
    GroupSharePort.ResolveResult.Expired -> throw ShareLinkExpiredException()
    GroupSharePort.ResolveResult.GroupUnavailable -> throw SharedGroupUnavailableException()
}
