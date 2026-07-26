package org.every.nook.api.presentation.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSearchProvider
import org.every.nook.api.application.place.SearchPlaceCandidatesUseCase
import org.every.nook.api.presentation.error.GlobalExceptionHandler
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import kotlin.test.BeforeTest
import kotlin.test.Test

class PlaceCandidateControllerTest {
    private lateinit var mockMvc: MockMvc

    @BeforeTest
    fun setUp() {
        val provider = PlaceSearchProvider {
            listOf(
                PlaceCandidate(
                    provider = "KAKAO",
                    externalPlaceId = "26338954",
                    name = "Nook Cafe",
                    address = "서울 성동구 성수동",
                    latitude = BigDecimal("37.5120741"),
                    longitude = BigDecimal("127.0590297"),
                    category = "음식점 > 카페",
                    phoneNumber = "02-1234-5678",
                    providerUrl = "https://place.map.kakao.com/26338954",
                ),
            )
        }
        mockMvc = MockMvcBuilders
            .standaloneSetup(PlaceCandidateController(SearchPlaceCandidatesUseCase(provider)))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `place candidates are returned with common response envelope`() {
        mockMvc.post("/api/v1/places/candidates/search") {
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "queries": ["Nook Cafe", "성수 카페"],
                  "longitude": 127.1,
                  "latitude": 37.1,
                  "radius": 1000
                }
                """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$.resultType") { value("SUCCESS") }
            jsonPath("$.success.candidates[0].provider") { value("KAKAO") }
            jsonPath("$.success.candidates[0].externalPlaceId") { value("26338954") }
            jsonPath("$.success.candidates[0].address") { value("서울 성동구 성수동") }
        }
    }

    @Test
    fun `empty query list is rejected`() {
        mockMvc.post("/api/v1/places/candidates/search") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"queries":[]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.errorCode") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun `partial coordinates are rejected by use case`() {
        mockMvc.post("/api/v1/places/candidates/search") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"queries":["Nook Cafe"],"longitude":127.1}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.errorCode") { value("PLACE_SEARCH_INVALID_REQUEST") }
        }
    }
}
