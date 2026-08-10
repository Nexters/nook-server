package org.every.nook.api.application.place

import org.every.nook.api.domain.place.PlaceThumbnailParsingStatus

enum class PlaceThumbnailParsingStatusView {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    ;

    companion object {
        fun from(status: PlaceThumbnailParsingStatus): PlaceThumbnailParsingStatusView = valueOf(status.name)
    }
}
