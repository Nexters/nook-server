package org.every.nook.api.application.place.port

import org.every.nook.api.domain.place.PlaceProviderReference

fun interface PlaceIdentityQueryPort {
    fun findIds(references: Set<PlaceProviderReference>): Map<PlaceProviderReference, Long>
}
