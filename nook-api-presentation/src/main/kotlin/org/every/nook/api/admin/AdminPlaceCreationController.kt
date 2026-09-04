package org.every.nook.api.admin

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.CreateAdminPlaceUseCase
import org.every.nook.api.application.place.PlaceOpeningHours
import org.every.nook.api.logging.RequestLoggingFields
import org.every.nook.api.presentation.response.ApiResponse
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/v1/places")
class AdminPlaceCreationController(private val createPlace: CreateAdminPlaceUseCase) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createPlace(
        actor: AdminActor,
        @Valid @RequestBody request: CreatePlaceRequest,
        servletRequest: HttpServletRequest,
    ) = ApiResponse.success(
        createPlace(
            CreateAdminPlaceUseCase.Command(
                name = request.name,
                address = request.address,
                category = request.category,
                phoneNumber = request.phoneNumber,
                thumbnailUrl = request.thumbnailUrl,
                photoUrls = request.photoUrls,
                representativeTags = request.representativeTags,
                openingHours = request.openingHours,
                actor = actor,
                reason = request.reason,
                requestId = MDC.get(RequestLoggingFields.REQUEST_ID)
                    ?: servletRequest.getHeader(RequestLoggingFields.REQUEST_ID_HEADER),
            ),
        ),
    )
}

data class CreatePlaceRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    @field:NotBlank
    @field:Size(max = 500)
    val address: String,
    val category: String? = null,
    val phoneNumber: String? = null,
    val thumbnailUrl: String? = null,
    @field:Size(max = 6)
    val photoUrls: List<String> = emptyList(),
    @field:Size(max = 4)
    val representativeTags: List<String> = emptyList(),
    val openingHours: PlaceOpeningHours? = null,
    @field:NotBlank
    @field:Size(max = 500)
    val reason: String,
)
