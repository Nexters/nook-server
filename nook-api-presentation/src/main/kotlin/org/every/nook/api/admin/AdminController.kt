package org.every.nook.api.admin

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.GetAdminPlaceUseCase
import org.every.nook.api.application.admin.GetAdminPostUseCase
import org.every.nook.api.application.admin.ListAdminAuditLogsUseCase
import org.every.nook.api.application.admin.ListAdminPlacesUseCase
import org.every.nook.api.application.admin.ListAdminPostsUseCase
import org.every.nook.api.application.admin.ReplaceAdminPostPlacesUseCase
import org.every.nook.api.application.admin.SearchAdminPlacesUseCase
import org.every.nook.api.application.admin.UpdateAdminPlaceUseCase
import org.every.nook.api.logging.RequestLoggingFields
import org.every.nook.api.presentation.response.ApiResponse
import org.slf4j.MDC
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/admin/v1")
class AdminController(
    private val listPosts: ListAdminPostsUseCase,
    private val getPost: GetAdminPostUseCase,
    private val searchPlaces: SearchAdminPlacesUseCase,
    private val listPlaces: ListAdminPlacesUseCase,
    private val getPlace: GetAdminPlaceUseCase,
    private val updatePlaceUseCase: UpdateAdminPlaceUseCase,
    private val replacePostPlaces: ReplaceAdminPostPlacesUseCase,
    private val listAuditLogs: ListAdminAuditLogsUseCase,
) {
    @GetMapping("/me")
    fun me(actor: AdminActor): ApiResponse<AdminActor> = ApiResponse.success(actor)

    @GetMapping("/posts")
    fun posts(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) parsingStatus: String?,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "20") limit: Int,
    ) = ApiResponse.success(
        listPosts(ListAdminPostsUseCase.Query(query, parsingStatus, offset, limit)),
    )

    @GetMapping("/posts/{postId}")
    fun post(@PathVariable postId: Long) = ApiResponse.success(getPost(postId))

    @PutMapping("/posts/{postId}/places")
    fun replacePlaces(
        actor: AdminActor,
        @PathVariable postId: Long,
        @Valid @RequestBody request: ReplacePostPlacesRequest,
        servletRequest: HttpServletRequest,
    ) = ApiResponse.success(
        replacePostPlaces(
            ReplaceAdminPostPlacesUseCase.Command(
                postId = postId,
                placeIds = request.placeIds,
                actor = actor,
                reason = request.reason,
                requestId = MDC.get(RequestLoggingFields.REQUEST_ID)
                    ?: servletRequest.getHeader(RequestLoggingFields.REQUEST_ID_HEADER),
            ),
        ),
    )

    @GetMapping("/places")
    fun places(@RequestParam query: String, @RequestParam(defaultValue = "20") limit: Int) =
        ApiResponse.success(searchPlaces(query, limit))

    @GetMapping("/places/manage")
    fun managedPlaces(
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "20") limit: Int,
    ) = ApiResponse.success(listPlaces(ListAdminPlacesUseCase.Query(query, offset, limit)))

    @GetMapping("/places/{placeId}")
    fun place(@PathVariable placeId: Long) = ApiResponse.success(getPlace(placeId))

    @PutMapping("/places/{placeId}")
    fun updatePlace(
        actor: AdminActor,
        @PathVariable placeId: Long,
        @Valid @RequestBody request: UpdatePlaceRequest,
        servletRequest: HttpServletRequest,
    ) = ApiResponse.success(
        updatePlaceUseCase(
            UpdateAdminPlaceUseCase.Command(
                placeId = placeId,
                name = request.name,
                address = request.address,
                actor = actor,
                reason = request.reason,
                requestId = MDC.get(RequestLoggingFields.REQUEST_ID)
                    ?: servletRequest.getHeader(RequestLoggingFields.REQUEST_ID_HEADER),
            ),
        ),
    )

    @GetMapping("/audit-logs")
    fun auditLogs(
        @RequestParam(required = false) targetType: String?,
        @RequestParam(required = false) targetId: String?,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "20") limit: Int,
    ) = ApiResponse.success(listAuditLogs(targetType, targetId, offset, limit))
}

data class ReplacePostPlacesRequest(
    @field:Size(max = 100)
    val placeIds: List<Long>,
    @field:NotBlank
    @field:Size(max = 500)
    val reason: String,
)

data class UpdatePlaceRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    @field:NotBlank
    @field:Size(max = 500)
    val address: String,
    @field:NotBlank
    @field:Size(max = 500)
    val reason: String,
)
