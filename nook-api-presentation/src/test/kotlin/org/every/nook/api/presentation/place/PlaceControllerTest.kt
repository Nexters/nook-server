package org.every.nook.api.presentation.place

import org.every.nook.api.application.place.UpdatePlaceBookmarkUseCase
import org.every.nook.api.presentation.auth.UserContextArgumentResolver
import org.every.nook.api.presentation.error.GlobalExceptionHandler
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.BeforeTest
import kotlin.test.Test

class PlaceControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var updatePlaceBookmarkUseCase: UpdatePlaceBookmarkUseCase

    @BeforeTest
    fun setUp() {
        updatePlaceBookmarkUseCase = mock(UpdatePlaceBookmarkUseCase::class.java)
        mockMvc = MockMvcBuilders
            .standaloneSetup(PlaceController(updatePlaceBookmarkUseCase))
            .setCustomArgumentResolvers(UserContextArgumentResolver())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `updates a place bookmark without a post identifier`() {
        mockMvc.patch("/api/v1/places/17/bookmark") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"bookmarked":false}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.resultType") { value("SUCCESS") }
        }

        verify(updatePlaceBookmarkUseCase)(
            UpdatePlaceBookmarkUseCase.Command(
                userId = UserContextArgumentResolver.DUMMY_USER_ID,
                placeId = 17,
                bookmarked = false,
            ),
        )
    }
}
