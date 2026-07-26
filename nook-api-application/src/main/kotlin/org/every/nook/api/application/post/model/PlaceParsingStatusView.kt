package org.every.nook.api.application.post.model

import org.every.nook.api.domain.place.PlaceParsingStatus

enum class PlaceParsingStatusView {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    ;

    companion object {
        fun from(status: PlaceParsingStatus): PlaceParsingStatusView = valueOf(status.name)
    }
}
