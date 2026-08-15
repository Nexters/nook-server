package org.every.nook.api.application.place

import org.every.nook.api.application.place.error.PlaceNotFoundException
import org.every.nook.api.application.place.port.UpdatePlaceMemoPort

class UpdatePlaceMemoUseCase(private val updatePlaceMemoPort: UpdatePlaceMemoPort) {
    operator fun invoke(command: Command) {
        val updated = updatePlaceMemoPort.update(
            userId = command.userId,
            placeId = command.placeId,
            memo = command.memo,
        )
        if (!updated) {
            throw PlaceNotFoundException()
        }
    }

    data class Command(val userId: Long, val placeId: Long, val memo: String?)
}
