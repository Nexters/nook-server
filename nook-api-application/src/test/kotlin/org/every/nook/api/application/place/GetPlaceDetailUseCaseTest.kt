package org.every.nook.api.application.place

import org.every.nook.api.application.place.error.PlaceNotFoundException
import org.every.nook.api.application.place.port.PlaceDetailQueryPort
import kotlin.test.Test
import kotlin.test.assertFailsWith

class GetPlaceDetailUseCaseTest {
    @Test
    fun `inaccessible place is exposed as not found`() {
        val useCase = GetPlaceDetailUseCase(PlaceDetailQueryPort { _, _, _, _ -> null })

        assertFailsWith<PlaceNotFoundException> {
            useCase(GetPlaceDetailUseCase.Query(userId = 7, placeId = 11, page = 0, size = 20))
        }
    }
}
