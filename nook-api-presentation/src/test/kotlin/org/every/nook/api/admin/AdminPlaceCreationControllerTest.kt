package org.every.nook.api.admin

import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.AdminPlaceAddressResolver
import org.every.nook.api.application.admin.AdminPlaceCreationPort
import org.every.nook.api.application.admin.AdminPlaceDetail
import org.every.nook.api.application.admin.CreateAdminPlaceUseCase
import org.every.nook.api.presentation.error.GlobalExceptionHandler
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class AdminPlaceCreationControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var captured: AdminPlaceCreationPort.CreateCommand
    private val actor = AdminActor("admin-subject", "admin@everynook.co.kr")

    @BeforeTest
    fun setUp() {
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken(actor, "credentials", "ROLE_ADMIN")
        val useCase = CreateAdminPlaceUseCase(
            addressResolver = AdminPlaceAddressResolver {
                AdminPlaceAddressResolver.ResolvedAddress(
                    "서울특별시 성동구 아차산로 17",
                    BigDecimal("37.5"),
                    BigDecimal("127.0"),
                )
            },
            creationPort = AdminPlaceCreationPort { command ->
                captured = command
                AdminPlaceDetail(
                    id = 17,
                    name = command.name,
                    address = command.address,
                    provider = command.provider,
                    externalPlaceId = command.externalPlaceId,
                    latitude = command.latitude.toPlainString(),
                    longitude = command.longitude.toPlainString(),
                )
            },
        )
        mockMvc = MockMvcBuilders
            .standaloneSetup(AdminPlaceCreationController(useCase))
            .setCustomArgumentResolvers(AdminActorArgumentResolver())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @AfterTest
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `creates a place from required manual input`() {
        mockMvc.post("/api/admin/v1/places") {
            contentType = MediaType.APPLICATION_JSON
            header("X-Request-ID", "request-17")
            content =
                """
                {
                  "name": "누크 카페",
                  "address": "서울 성동구 아차산로 17",
                  "reason": "운영자 신규 등록"
                }
                """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.success.id") { value(17) }
            jsonPath("$.success.provider") { value("MANUAL") }
            jsonPath("$.success.address") { value("서울특별시 성동구 아차산로 17") }
        }

        kotlin.test.assertEquals("누크 카페", captured.name)
        kotlin.test.assertEquals("서울특별시 성동구 아차산로 17", captured.address)
        kotlin.test.assertEquals(actor, captured.actor)
        kotlin.test.assertEquals("운영자 신규 등록", captured.reason)
        kotlin.test.assertEquals("request-17", captured.requestId)
    }
}
