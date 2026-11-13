package org.every.nook.api.application.reminder

import org.every.nook.api.application.post.port.UnlinkedSavedPostQueryPort

data class PlaceReminderView(val enabled: Boolean, val unlinkedPostCount: Long) {
    val showBanner: Boolean get() = enabled && unlinkedPostCount > 0
}

class GetPlaceReminderUseCase(
    private val preferencePort: PlaceReminderPreferencePort,
    private val queryPort: UnlinkedSavedPostQueryPort,
) {
    operator fun invoke(query: Query): PlaceReminderView = PlaceReminderView(
        enabled = preferencePort.isEnabled(query.userId),
        unlinkedPostCount = queryPort.countUnlinked(query.userId),
    )

    data class Query(val userId: Long)
}

class UpdatePlaceReminderUseCase(
    private val preferencePort: PlaceReminderPreferencePort,
    private val queryPort: UnlinkedSavedPostQueryPort,
) {
    operator fun invoke(command: Command): PlaceReminderView = PlaceReminderView(
        enabled = preferencePort.update(command.userId, command.enabled),
        unlinkedPostCount = queryPort.countUnlinked(command.userId),
    )

    data class Command(val userId: Long, val enabled: Boolean)
}
