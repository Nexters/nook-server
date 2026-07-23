package org.every.nook.api.presentation.error

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.every.nook.api.application.error.ErrorType
import org.every.nook.api.application.error.NookErrorCode
import org.every.nook.api.application.error.NookException
import org.every.nook.api.presentation.response.ApiResponse
import org.hamcrest.Matchers.nullValue
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import kotlin.test.BeforeTest
import kotlin.test.Test

class GlobalExceptionHandlerTest {
    private lateinit var mockMvc: MockMvc

    @BeforeTest
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(ErrorTestController())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `success response contains only success body`() {
        mockMvc.get("/test/success")
            .andExpect {
                status { isOk() }
                jsonPath("$.resultType") { value("SUCCESS") }
                jsonPath("$.success.id") { value(1) }
                jsonPath("$.error") { doesNotExist() }
            }
    }

    @Test
    fun `nook exception is converted to its http status`() {
        mockMvc.get("/test/nook-error")
            .andExpect {
                status { isConflict() }
                jsonPath("$.resultType") { value("FAIL") }
                jsonPath("$.success") { doesNotExist() }
                jsonPath("$.error.errorCode") { value("TEST_CONFLICT") }
                jsonPath("$.error.reason") { value("Test conflict.") }
                jsonPath("$.error.data") { value(nullValue()) }
            }
    }

    @Test
    fun `validation error contains field violations`() {
        mockMvc.post("/test/validation") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":""}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.resultType") { value("FAIL") }
            jsonPath("$.error.errorCode") { value("INVALID_REQUEST") }
            jsonPath("$.error.data.violations[0].field") { value("name") }
        }
    }

    @Test
    fun `malformed request body is an invalid request`() {
        mockMvc.post("/test/validation") {
            contentType = MediaType.APPLICATION_JSON
            content = "{"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.errorCode") { value("INVALID_REQUEST") }
            jsonPath("$.error.data") { value(nullValue()) }
        }
    }

    @Test
    fun `unexpected exception does not expose its message`() {
        mockMvc.get("/test/unexpected")
            .andExpect {
                status { isInternalServerError() }
                jsonPath("$.error.errorCode") { value("INTERNAL_SERVER_ERROR") }
                jsonPath("$.error.reason") { value("서버 오류가 발생했습니다.") }
                content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))) }
            }
    }
}

@RestController
private class ErrorTestController {
    @GetMapping("/test/success")
    fun success(): ApiResponse<Map<String, Int>> = ApiResponse.success(mapOf("id" to 1))

    @GetMapping("/test/nook-error")
    fun nookError(): Nothing = throw TestNookException()

    @PostMapping("/test/validation")
    fun validation(@Valid @RequestBody request: ValidationRequest): ApiResponse<ValidationRequest> =
        ApiResponse.success(request)

    @GetMapping("/test/unexpected")
    fun unexpected(): Nothing = error("secret")
}

private data class ValidationRequest(@field:NotBlank val name: String)

private class TestNookException : NookException(TestErrorCode)

private data object TestErrorCode : NookErrorCode {
    override val code: String = "TEST_CONFLICT"
    override val defaultReason: String = "Test conflict."
    override val type: ErrorType = ErrorType.CONFLICT
}
