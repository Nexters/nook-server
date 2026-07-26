package org.every.nook.api.presentation.save

import org.every.nook.api.application.save.FindSavedPostPlaceParsingUseCase
import org.every.nook.api.application.save.SaveInstagramPostUseCase
import org.every.nook.api.application.save.model.PlaceParsingStatusView
import org.every.nook.api.application.save.model.PlaceView
import org.every.nook.api.presentation.error.GlobalExceptionHandler
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import kotlin.test.BeforeTest
import kotlin.test.Test

class SavedPostControllerTest {
    private lateinit var mockMvc: MockMvc

    @BeforeTest
    fun setUp() {
        val saveUseCase = mock(SaveInstagramPostUseCase::class.java)
        `when`(
            saveUseCase(
                SaveInstagramPostUseCase.Command(
                    userId = 7,
                    instagramUrl = "https://www.instagram.com/p/ABC123/",
                ),
            ),
        ).thenReturn(
            SaveInstagramPostUseCase.Result(
                savedPostId = 11,
                postId = 13,
                placeParsingStatus = PlaceParsingStatusView.PENDING,
            ),
        )
        val findUseCase = mock(FindSavedPostPlaceParsingUseCase::class.java)
        `when`(
            findUseCase(FindSavedPostPlaceParsingUseCase.Query(userId = 7, savedPostId = 11)),
        ).thenReturn(
            FindSavedPostPlaceParsingUseCase.Result(
                savedPostId = 11,
                postId = 13,
                placeParsingStatus = PlaceParsingStatusView.COMPLETED,
                failureReason = null,
                places = listOf(
                    PlaceView(
                        id = 17,
                        provider = "KAKAO",
                        externalPlaceId = "123",
                        name = "Nook Cafe",
                        address = "Seoul",
                        latitude = BigDecimal("37.1"),
                        longitude = BigDecimal("127.1"),
                        category = null,
                        phoneNumber = null,
                    ),
                ),
            ),
        )
        mockMvc = MockMvcBuilders
            .standaloneSetup(SavedPostController(saveUseCase, findUseCase))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `creates an Instagram saved post`() {
        mockMvc.post("/api/v1/saved-posts") {
            header(SavedPostController.USER_ID_HEADER, 7)
            contentType = MediaType.APPLICATION_JSON
            content = """{"instagramUrl":"https://www.instagram.com/p/ABC123/"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.resultType") { value("SUCCESS") }
            jsonPath("$.success.savedPostId") { value(11) }
            jsonPath("$.success.postId") { value(13) }
            jsonPath("$.success.placeParsingStatus") { value("PENDING") }
        }
    }

    @Test
    fun `returns completed parsing with places`() {
        mockMvc.get("/api/v1/saved-posts/11/place-parsing") {
            header(SavedPostController.USER_ID_HEADER, 7)
        }.andExpect {
            status { isOk() }
            jsonPath("$.success.placeParsingStatus") { value("COMPLETED") }
            jsonPath("$.success.places[0].id") { value(17) }
            jsonPath("$.success.places[0].name") { value("Nook Cafe") }
        }
    }

    @Test
    fun `rejects a blank Instagram URL`() {
        mockMvc.post("/api/v1/saved-posts") {
            header(SavedPostController.USER_ID_HEADER, 7)
            contentType = MediaType.APPLICATION_JSON
            content = """{"instagramUrl":""}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.errorCode") { value("INVALID_REQUEST") }
            jsonPath("$.error.data.violations[0].field") { value("instagramUrl") }
        }
    }
}
