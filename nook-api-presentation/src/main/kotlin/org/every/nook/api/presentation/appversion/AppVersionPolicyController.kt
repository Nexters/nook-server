package org.every.nook.api.presentation.appversion

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.PositiveOrZero
import org.every.nook.api.application.appversion.AppPlatform
import org.every.nook.api.application.appversion.AppUpdateType
import org.every.nook.api.application.appversion.GetAppVersionPolicyUseCase
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "App Version")
@Validated
@RestController
@RequestMapping("/api/public/v1/app-version-policy")
class AppVersionPolicyController(private val getAppVersionPolicyUseCase: GetAppVersionPolicyUseCase) {
    @Operation(summary = "앱 버전 정책 조회")
    @GetMapping
    fun getPolicy(
        @RequestHeader("X-App-Platform") platform: AppPlatform,
        @RequestHeader("X-App-Build-Number") @PositiveOrZero buildNumber: Long,
    ): ApiResponse<AppVersionPolicyResponse> {
        val policy = getAppVersionPolicyUseCase(GetAppVersionPolicyUseCase.Query(platform, buildNumber))
        return ApiResponse.success(
            AppVersionPolicyResponse(
                updateType = policy.updateType,
                latestBuildNumber = policy.latestBuildNumber,
                latestVersion = policy.latestVersion,
                storeUrl = policy.storeUrl,
            ),
        )
    }
}

data class AppVersionPolicyResponse(
    val updateType: AppUpdateType,
    val latestBuildNumber: Long?,
    val latestVersion: String?,
    val storeUrl: String?,
)
