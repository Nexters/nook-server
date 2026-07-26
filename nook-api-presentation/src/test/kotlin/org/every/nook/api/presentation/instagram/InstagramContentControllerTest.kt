package org.every.nook.api.presentation.instagram

import org.every.nook.api.application.instagram.ExtractInstagramContentUseCase
import org.every.nook.api.application.instagram.ExtractedInstagramContent
import org.every.nook.api.application.instagram.InstagramContentProvider
import org.every.nook.api.presentation.error.GlobalExceptionHandler
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.BeforeTest
import kotlin.test.Test

class InstagramContentControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var useCase: ExtractInstagramContentUseCase

    @BeforeTest
    fun setUp() {
        useCase = mock(ExtractInstagramContentUseCase::class.java)
        mockMvc = MockMvcBuilders
            .standaloneSetup(InstagramContentController(useCase))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `extracted content is returned with common response envelope`() {
        val extractedContent = mock(ExtractedInstagramContent::class.java)
        `when`(extractedContent.canonicalUrl).thenReturn("https://www.instagram.com/p/Post123/")
        `when`(extractedContent.shortcode).thenReturn("Post123")
        `when`(extractedContent.contentType).thenReturn(ExtractedInstagramContent.ContentType.IMAGE)
        `when`(extractedContent.description).thenReturn("카페")
        `when`(extractedContent.hashtags).thenReturn(listOf("#카페"))
        `when`(extractedContent.thumbnailUrl).thenReturn("https://cdn.example/thumbnail.jpg")
        `when`(extractedContent.media).thenReturn(
            listOf(
                ExtractedInstagramContent.Media(
                    type = ExtractedInstagramContent.MediaType.IMAGE,
                    url = "https://cdn.example/photo.jpg",
                    sequence = 0,
                ),
            ),
        )
        `when`(extractedContent.locationNames).thenReturn(listOf("Nook Cafe"))
        `when`(extractedContent.locationDetails).thenReturn(
            ExtractedInstagramContent.LocationDetails(
                id = "place-1",
                name = "Nook Cafe",
                latitude = 37.1,
                longitude = 127.1,
                imageUrl = null,
            ),
        )
        `when`(useCase("https://www.instagram.com/p/Post123/")).thenReturn(extractedContent)

        mockMvc.post("/api/v1/instagram/contents/extract") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"url":"https://www.instagram.com/p/Post123/"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.resultType") { value("SUCCESS") }
            jsonPath("$.success.shortcode") { value("Post123") }
            jsonPath("$.success.media[0].type") { value("IMAGE") }
            jsonPath("$.success.locationDetails.name") { value("Nook Cafe") }
        }
    }

    @Test
    fun `unsupported Instagram URL is a bad request`() {
        val validatingUseCase = ExtractInstagramContentUseCase(
            InstagramContentProvider { error("provider must not be called") },
        )
        val validatingMockMvc = MockMvcBuilders
            .standaloneSetup(InstagramContentController(validatingUseCase))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

        validatingMockMvc.post("/api/v1/instagram/contents/extract") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"url":"https://evil.example/p/Post123/"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.resultType") { value("FAIL") }
            jsonPath("$.error.errorCode") { value("INSTAGRAM_INVALID_URL") }
        }
    }

    @Test
    fun `blank URL is rejected by request validation`() {
        mockMvc.post("/api/v1/instagram/contents/extract") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"url":""}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.errorCode") { value("INVALID_REQUEST") }
        }
    }
}
