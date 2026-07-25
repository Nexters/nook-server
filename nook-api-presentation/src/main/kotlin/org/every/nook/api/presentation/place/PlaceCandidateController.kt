package org.every.nook.api.presentation.place

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.SearchPlaceCandidatesUseCase
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@Tag(name = "Place Candidate")
@RestController
@RequestMapping("/api/v1/places/candidates")
class PlaceCandidateController(private val searchPlaceCandidatesUseCase: SearchPlaceCandidatesUseCase) {
    @Operation(summary = "검색어 기반 장소 후보 조회")
    @PostMapping("/search")
    fun search(@Valid @RequestBody request: SearchPlaceCandidatesRequest): ApiResponse<SearchPlaceCandidatesResponse> {
        val candidates = searchPlaceCandidatesUseCase(request.toCommand())
        return ApiResponse.success(SearchPlaceCandidatesResponse(candidates.map(PlaceCandidateResponse::from)))
    }
}

data class SearchPlaceCandidatesRequest(
    @field:NotEmpty
    @field:Size(max = 10)
    val queries: List<String>,
    val longitude: BigDecimal? = null,
    val latitude: BigDecimal? = null,
    val radius: Int? = null,
) {
    fun toCommand(): SearchPlaceCandidatesUseCase.Command = SearchPlaceCandidatesUseCase.Command(
        queries = queries,
        longitude = longitude,
        latitude = latitude,
        radius = radius,
    )
}

data class SearchPlaceCandidatesResponse(val candidates: List<PlaceCandidateResponse>)

data class PlaceCandidateResponse(
    val provider: String,
    val externalPlaceId: String,
    val name: String,
    val address: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val category: String?,
    val phoneNumber: String?,
    val providerUrl: String?,
) {
    companion object {
        fun from(candidate: PlaceCandidate): PlaceCandidateResponse = PlaceCandidateResponse(
            provider = candidate.provider,
            externalPlaceId = candidate.externalPlaceId,
            name = candidate.name,
            address = candidate.address,
            latitude = candidate.latitude,
            longitude = candidate.longitude,
            category = candidate.category,
            phoneNumber = candidate.phoneNumber,
            providerUrl = candidate.providerUrl,
        )
    }
}
