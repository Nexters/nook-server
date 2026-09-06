package org.every.nook.api.application.place

import org.every.nook.api.application.analytics.UserAnalyticsEventName
import org.every.nook.api.application.analytics.UserAnalyticsEventRecorder
import org.every.nook.api.application.analytics.UserAnalyticsTargetType
import org.every.nook.api.application.place.error.PlaceNotFoundException
import org.every.nook.api.application.place.port.PlaceBookmarkUpdateResult
import org.every.nook.api.application.place.port.UpdatePlaceBookmarkPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UpdatePlaceBookmarkUseCaseTest {
    @Test
    fun `updates a user place bookmark without a post identifier`() {
        var update: List<Any>? = null
        val useCase = UpdatePlaceBookmarkUseCase(
            UpdatePlaceBookmarkPort { userId, placeId, bookmarked ->
                update = listOf(userId, placeId, bookmarked)
                PlaceBookmarkUpdateResult(accessible = true, changed = true)
            },
        )

        useCase(UpdatePlaceBookmarkUseCase.Command(userId = 7, placeId = 17, bookmarked = false))

        assertEquals(listOf(7L, 17L, false), update)
    }

    @Test
    fun `fails when the place is not available to the user`() {
        val useCase = UpdatePlaceBookmarkUseCase(
            UpdatePlaceBookmarkPort { _, _, _ -> PlaceBookmarkUpdateResult(accessible = false, changed = false) },
        )

        assertFailsWith<PlaceNotFoundException> {
            useCase(UpdatePlaceBookmarkUseCase.Command(userId = 7, placeId = 17, bookmarked = true))
        }
    }

    @Test
    fun `records only a bookmark state transition to saved`() {
        val events = mutableListOf<List<Any?>>()
        val useCase = UpdatePlaceBookmarkUseCase(
            updatePlaceBookmarkPort = UpdatePlaceBookmarkPort { _, _, _ ->
                PlaceBookmarkUpdateResult(accessible = true, changed = true)
            },
            analyticsRecorder = UserAnalyticsEventRecorder { record ->
                events += listOf(record.eventName, record.memberId, record.targetType, record.targetId)
            },
        )

        useCase(UpdatePlaceBookmarkUseCase.Command(userId = 7, placeId = 17, bookmarked = true))

        assertEquals<List<List<Any?>>>(
            listOf(listOf(UserAnalyticsEventName.PLACE_SAVE, 7L, UserAnalyticsTargetType.PLACE, 17L)),
            events,
        )
    }
}
