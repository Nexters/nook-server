package org.every.nook.api.presentation.appversion

import org.every.nook.api.application.appversion.AppUpdateType
import org.every.nook.api.application.appversion.AppVersionPolicy
import org.every.nook.api.application.appversion.AppVersionPolicyPort
import org.every.nook.api.application.appversion.GetAppVersionPolicyUseCase
import org.every.nook.api.presentation.error.GlobalExceptionHandler
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.BeforeTest
import kotlin.test.Test

class AppVersionPolicyControllerTest {
    private lateinit var mockMvc: MockMvc

    @BeforeTest
    fun setUp() {
        val useCase = GetAppVersionPolicyUseCase(
            AppVersionPolicyPort {
                AppVersionPolicy(it, 10, 20, "1.2.0", "https://example.com/store")
            },
        )
        mockMvc = MockMvcBuilders
            .standaloneSetup(AppVersionPolicyController(useCase))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `returns platform policy based on current build number`() {
        mockMvc.get("/api/public/v1/app-version-policy") {
            header("X-App-Platform", "IOS")
            header("X-App-Build-Number", "9")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.success.updateType") { value(AppUpdateType.FORCE.name) }
                jsonPath("$.success.latestBuildNumber") { value(20) }
                jsonPath("$.success.latestVersion") { value("1.2.0") }
                jsonPath("$.success.storeUrl") { value("https://example.com/store") }
            }
    }

    @Test
    fun `rejects unsupported platform`() {
        mockMvc.get("/api/public/v1/app-version-policy") {
            header("X-App-Platform", "WEB")
            header("X-App-Build-Number", "1")
        }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `requires app context headers`() {
        mockMvc.get("/api/public/v1/app-version-policy")
            .andExpect { status { isBadRequest() } }
    }
}
