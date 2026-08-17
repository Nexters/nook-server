package org.every.nook.api.application.group

import java.time.Instant

data class GroupShareLinkView(val token: String, val expiresAt: Instant?)

data class SharedGroupAccess(val shareLinkId: Long, val groupId: Long, val ownerId: Long, val token: String)

data class SharedGroupView(val group: GroupView, val owner: GroupOwnerView)
