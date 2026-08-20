package org.every.nook.api.admin

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.ListAdminPlaceTagsUseCase
import org.every.nook.api.application.admin.UpdateAdminPlaceTagUseCase
import org.every.nook.api.logging.RequestLoggingFields
import org.every.nook.api.presentation.response.ApiResponse
import org.slf4j.MDC
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/v1/place-tags")
class AdminPlaceTagController(
    private val listPlaceTags: ListAdminPlaceTagsUseCase,
    private val updatePlaceTag: UpdateAdminPlaceTagUseCase,
) {
    @GetMapping
    fun placeTags(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) enabled: Boolean?,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "100") limit: Int,
    ) = ApiResponse.success(listPlaceTags(ListAdminPlaceTagsUseCase.Query(category, enabled, offset, limit)))

    @PutMapping("/{tagCode}")
    fun updatePlaceTag(
        actor: AdminActor,
        @PathVariable tagCode: String,
        @Valid @RequestBody request: UpdatePlaceTagRequest,
        servletRequest: HttpServletRequest,
    ) = ApiResponse.success(
        updatePlaceTag(
            UpdateAdminPlaceTagUseCase.Command(
                tagCode = tagCode,
                category = request.category,
                displayName = request.displayName,
                matchingKeywords = request.matchingKeywords,
                enabled = request.enabled,
                sortOrder = request.sortOrder,
                actor = actor,
                reason = request.reason,
                requestId = MDC.get(RequestLoggingFields.REQUEST_ID)
                    ?: servletRequest.getHeader(RequestLoggingFields.REQUEST_ID_HEADER),
            ),
        ),
    )
}

data class UpdatePlaceTagRequest(
    @field:NotBlank
    val category: String,
    @field:NotBlank
    @field:Size(max = 50)
    val displayName: String,
    @field:Size(min = 1, max = 20)
    val matchingKeywords: List<String>,
    val enabled: Boolean,
    val sortOrder: Int,
    @field:NotBlank
    @field:Size(max = 500)
    val reason: String,
)
