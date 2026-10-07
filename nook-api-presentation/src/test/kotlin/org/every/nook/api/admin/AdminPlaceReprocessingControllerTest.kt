package org.every.nook.api.admin

import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.AdminPlaceNotFoundException
import org.every.nook.api.application.admin.ClaimedPlaceReprocessing
import org.every.nook.api.application.admin.GetPlaceReprocessingUseCase
import org.every.nook.api.application.admin.PlaceReprocessingActiveException
import org.every.nook.api.application.admin.PlaceReprocessingJob
import org.every.nook.api.application.admin.PlaceReprocessingOutcome
import org.every.nook.api.application.admin.PlaceReprocessingPort
import org.every.nook.api.application.admin.PlaceReprocessingResult
import org.every.nook.api.application.admin.PlaceReprocessingStatus
import org.every.nook.api.application.admin.RequestPlaceReprocessingCommand
import org.every.nook.api.application.admin.RequestPlaceReprocessingUseCase
import org.every.nook.api.presentation.error.GlobalExceptionHandler
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminPlaceReprocessingControllerTest {
    private lateinit var mvc: MockMvc
    private var captured: RequestPlaceReprocessingCommand? = null
    private var conflict = false
    private val actor = AdminActor("admin", "admin@example.com")
    private val job = PlaceReprocessingJob(
        17,
        PlaceReprocessingStatus.PENDING,
        0,
        PlaceReprocessingOutcome.PENDING,
        PlaceReprocessingOutcome.PENDING,
        Instant.parse("2026-10-07T14:00:00Z"),
    )
    private val port = object : PlaceReprocessingPort {
        override fun enqueue(command: RequestPlaceReprocessingCommand): PlaceReprocessingJob {
            if (conflict) throw PlaceReprocessingActiveException()
            captured = command
            return job
        }
        override fun find(placeId: Long): PlaceReprocessingJob? {
            if (placeId != 17L) throw AdminPlaceNotFoundException()
            return null
        }
        override fun claim(): ClaimedPlaceReprocessing? = null
        override fun finish(job: ClaimedPlaceReprocessing, result: PlaceReprocessingResult): Boolean = false
        override fun fail(job: ClaimedPlaceReprocessing): Boolean = false
    }

    @BeforeTest
    fun setUp() {
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken(actor, "", "ROLE_ADMIN")
        mvc = MockMvcBuilders.standaloneSetup(
            AdminPlaceReprocessingController(
                RequestPlaceReprocessingUseCase(port),
                GetPlaceReprocessingUseCase(port),
            ),
        ).setCustomArgumentResolvers(AdminActorArgumentResolver())
            .setControllerAdvice(GlobalExceptionHandler()).build()
    }

    @AfterTest
    fun cleanUp() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `reserves all three stages with actor reason and request id`() {
        mvc.post("/api/admin/v1/places/17/reprocessing") {
            contentType = MediaType.APPLICATION_JSON
            header("X-Request-ID", "request-17")
            content = """{"reason":"  부가 정보 재수집  "}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.success.job.placeId") { value(17) }
            jsonPath("$.success.job.status") { value("PENDING") }
            jsonPath("$.success.job.photos") { value("PENDING") }
            jsonPath("$.success.job.tags") { value("PENDING") }
        }
        assertEquals(actor, captured?.actor)
        assertEquals("부가 정보 재수집", captured?.reason)
        assertEquals("request-17", captured?.requestId)
    }

    @Test
    fun `returns no history separately from unknown place`() {
        mvc.get("/api/admin/v1/places/17/reprocessing").andExpect {
            status { isOk() }
            jsonPath("$.success.job") { doesNotExist() }
        }
        mvc.get("/api/admin/v1/places/999/reprocessing").andExpect { status { isNotFound() } }
    }

    @Test
    fun `rejects duplicate request and blank reason`() {
        conflict = true
        mvc.post("/api/admin/v1/places/17/reprocessing") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason":"재시도"}"""
        }.andExpect { status { isConflict() } }
        mvc.post("/api/admin/v1/places/17/reprocessing") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason":" "}"""
        }.andExpect { status { isBadRequest() } }
    }
}
