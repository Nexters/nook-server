package org.every.nook.api.application.place

import org.every.nook.api.application.place.error.PlaceNotFoundException
import org.every.nook.api.application.place.port.DisconnectPostPlacePort
import org.every.nook.api.application.post.error.PostNotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DisconnectPostPlaceUseCaseTest {
    @Test
    fun `disconnects a place from the owned saved post`() {
        var command: Triple<Long, Long, Long>? = null
        val useCase = DisconnectPostPlaceUseCase { userId, postId, placeId ->
            command = Triple(userId, postId, placeId)
            DisconnectPostPlacePort.Result.DISCONNECTED
        }

        useCase(DisconnectPostPlaceUseCase.Command(userId = 7, postId = 11, placeId = 17))

        assertEquals(Triple(7L, 11L, 17L), command)
    }

    @Test
    fun `missing saved post throws post not found`() {
        val useCase = DisconnectPostPlaceUseCase { _, _, _ ->
            DisconnectPostPlacePort.Result.POST_NOT_FOUND
        }

        assertFailsWith<PostNotFoundException> {
            useCase(DisconnectPostPlaceUseCase.Command(userId = 7, postId = 11, placeId = 17))
        }
    }

    @Test
    fun `missing relation throws place not found`() {
        val useCase = DisconnectPostPlaceUseCase { _, _, _ ->
            DisconnectPostPlacePort.Result.PLACE_NOT_CONNECTED
        }

        assertFailsWith<PlaceNotFoundException> {
            useCase(DisconnectPostPlaceUseCase.Command(userId = 7, postId = 11, placeId = 17))
        }
    }
}
