package org.every.nook.api.presentation.response

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

private val SEOUL_ZONE = ZoneId.of("Asia/Seoul")

internal fun Instant.toSeoulOffsetDateTime(): OffsetDateTime = atZone(SEOUL_ZONE).toOffsetDateTime()
