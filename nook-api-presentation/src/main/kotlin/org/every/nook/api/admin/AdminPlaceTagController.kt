package org.every.nook.api.admin

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.CreateAdminPlaceTagUseCase
import org.every.nook.api.application.admin.DeleteAdminPlaceTagUseCase
import org.every.nook.api.application.admin.ListAdminPlaceTagsUseCase
import org.every.nook.api.application.admin.ReorderAdminPlaceTagsUseCase
import org.every.nook.api.application.admin.UpdateAdminPlaceTagUseCase
import org.every.nook.api.logging.RequestLoggingFields
import org.every.nook.api.presentation.response.ApiResponse
import org.slf4j.MDC
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
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
    private val createPlaceTag: CreateAdminPlaceTagUseCase,
    private val reorderPlaceTags: ReorderAdminPlaceTagsUseCase,
    private val deletePlaceTag: DeleteAdminPlaceTagUseCase,
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

    @PostMapping
    fun createPlaceTag(
        actor: AdminActor,
        @Valid @RequestBody request: CreatePlaceTagRequest,
        servletRequest: HttpServletRequest,
    ) = ApiResponse.success(
        createPlaceTag(
            CreateAdminPlaceTagUseCase.Command(
                request.category,
                request.displayName,
                request.matchingKeywords,
                actor,
                request.reason,
                requestId(servletRequest),
            ),
        ),
    )

    @PutMapping("/order")
    fun reorderPlaceTags(
        actor: AdminActor,
        @Valid @RequestBody request: ReorderPlaceTagsRequest,
        servletRequest: HttpServletRequest,
    ) = ApiResponse.success(
        reorderPlaceTags(
            ReorderAdminPlaceTagsUseCase.Command(
                request.tagCodes,
                actor,
                request.reason,
                requestId(servletRequest),
            ),
        ),
    )

    @DeleteMapping("/{tagCode}")
    fun deletePlaceTag(
        actor: AdminActor,
        @PathVariable tagCode: String,
        @Valid @RequestBody request: DeletePlaceTagRequest,
        servletRequest: HttpServletRequest,
    ) = ApiResponse.success(
        deletePlaceTag(
            DeleteAdminPlaceTagUseCase.Command(
                tagCode,
                request.replacementTagCode,
                actor,
                request.reason,
                requestId(servletRequest),
            ),
        ),
    )

    private fun requestId(request: HttpServletRequest) = MDC.get(RequestLoggingFields.REQUEST_ID)
        ?: request.getHeader(RequestLoggingFields.REQUEST_ID_HEADER)
}

data class CreatePlaceTagRequest(
    @field:NotBlank val category: String,
    @field:NotBlank @field:Size(max = 50) val displayName: String,
    @field:Size(min = 1, max = 20) val matchingKeywords: List<String>,
    @field:NotBlank @field:Size(max = 500) val reason: String,
)

data class ReorderPlaceTagsRequest(
    @field:Size(min = 1) val tagCodes: List<String>,
    @field:NotBlank @field:Size(max = 500) val reason: String,
)

data class DeletePlaceTagRequest(
    @field:NotBlank val replacementTagCode: String,
    @field:NotBlank @field:Size(max = 500) val reason: String,
)

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
