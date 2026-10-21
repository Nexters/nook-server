package org.every.nook.api.application.group.port

import org.every.nook.api.application.group.GroupSubscriberPage
import org.every.nook.api.application.group.GroupSubscriberSummary

interface GroupSubscribersPort {
    fun findSummaries(groupIds: Set<Long>): Map<Long, GroupSubscriberSummary>

    fun findPage(groupId: Long, page: Int, size: Int): GroupSubscriberPage
}
