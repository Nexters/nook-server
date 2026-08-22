package org.every.nook.api.admin

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.ListAdminAppVersionPoliciesUseCase
import org.every.nook.api.application.admin.UpsertAdminAppVersionPolicyUseCase
import org.every.nook.api.application.appversion.AppPlatform
import org.every.nook.api.application.appversion.AppVersionPolicy
import org.every.nook.api.logging.RequestLoggingFields
import org.every.nook.api.presentation.response.ApiResponse
import org.slf4j.MDC
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/admin/v1/app-version-policies")
class AdminAppVersionPolicyController(
    private val listPolicies: ListAdminAppVersionPoliciesUseCase,
    private val upsertPolicy: UpsertAdminAppVersionPolicyUseCase,
) {
    @GetMapping
    fun policies(): ApiResponse<List<AppVersionPolicy>> = ApiResponse.success(listPolicies())

    @PutMapping("/{platform}")
    fun upsert(
        actor: AdminActor,
        @PathVariable platform: AppPlatform,
        @Valid @RequestBody request: UpsertAppVersionPolicyRequest,
        servletRequest: HttpServletRequest,
    ): ApiResponse<AppVersionPolicy> = ApiResponse.success(
        upsertPolicy(
            UpsertAdminAppVersionPolicyUseCase.Command(
                platform = platform,
                minimumSupportedBuildNumber = request.minimumSupportedBuildNumber,
                latestBuildNumber = request.latestBuildNumber,
                latestVersion = request.latestVersion,
                storeUrl = request.storeUrl,
                actor = actor,
                reason = request.reason,
                requestId = MDC.get(RequestLoggingFields.REQUEST_ID)
                    ?: servletRequest.getHeader(RequestLoggingFields.REQUEST_ID_HEADER),
            ),
        ),
    )
}

data class UpsertAppVersionPolicyRequest(
    @field:PositiveOrZero
    val minimumSupportedBuildNumber: Long,
    @field:PositiveOrZero
    val latestBuildNumber: Long,
    @field:NotBlank
    @field:Size(max = AppVersionPolicy.MAX_VERSION_LENGTH)
    val latestVersion: String,
    @field:NotBlank
    @field:Size(max = AppVersionPolicy.MAX_STORE_URL_LENGTH)
    val storeUrl: String,
    @field:NotBlank
    @field:Size(max = 500)
    val reason: String,
)
