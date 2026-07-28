package org.every.nook.api.application.place

import java.time.Instant

data class PlaceParsingJobRequestedEvent(val postId: Long, val availableAt: Instant? = null)
