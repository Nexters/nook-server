package org.every.nook.api.application.post

import java.time.Instant

data class PostContentParsingJobRequestedEvent(val postId: Long, val availableAt: Instant? = null)
