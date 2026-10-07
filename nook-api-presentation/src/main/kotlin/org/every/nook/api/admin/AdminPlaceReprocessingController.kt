package org.every.nook.api.admin

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.GetPlaceReprocessingUseCase
import org.every.nook.api.application.admin.PlaceReprocessingJob
import org.every.nook.api.application.admin.RequestPlaceReprocessingCommand
import org.every.nook.api.application.admin.RequestPlaceReprocessingUseCase
import org.every.nook.api.logging.RequestLoggingFields
import org.every.nook.api.presentation.response.ApiResponse
import org.slf4j.MDC
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/admin/v1/places/{placeId}/reprocessing")
@Tag(name = "Admin Place Reprocessing")
class AdminPlaceReprocessingController(
    private val requestReprocessing: RequestPlaceReprocessingUseCase,
    private val getReprocessing: GetPlaceReprocessingUseCase,
) {
    @PostMapping
    @Operation(summary = "장소의 사진·태그 재처리를 예약합니다")
    fun request(
        @Parameter(description = "재처리할 장소 ID") @PathVariable placeId: Long,
        @Valid @RequestBody request: ReprocessRequest,
        actor: AdminActor,
        servletRequest: HttpServletRequest,
    ) = ApiResponse.success(
        ReprocessingResponse(
            requestReprocessing(
                RequestPlaceReprocessingCommand(
                    placeId,
                    actor,
                    request.reason,
                    MDC.get(RequestLoggingFields.REQUEST_ID)
                        ?: servletRequest.getHeader(RequestLoggingFields.REQUEST_ID_HEADER),
                ),
            ),
        ),
    )

    @GetMapping
    @Operation(summary = "장소 재처리의 진행 상태와 항목별 결과를 조회합니다")
    fun get(@Parameter(description = "조회할 장소 ID") @PathVariable placeId: Long) =
        ApiResponse.success(ReprocessingResponse(getReprocessing(placeId)))

    data class ReprocessRequest(
        @field:Schema(description = "재처리 사유; 최대 500자")
        @field:NotBlank
        @field:Size(max = 500)
        val reason: String,
    )

    data class ReprocessingResponse(
        @field:Schema(description = "최신 재처리 작업; 실행 이력이 없으면 null")
        val job: JobResponse?,
    ) {
        constructor(job: PlaceReprocessingJob?) : this(job?.let(JobResponse::from))
    }

    data class JobResponse(
        @field:Schema(description = "대상 장소 ID") val placeId: Long,
        @field:Schema(description = "PENDING, PROCESSING, COMPLETED, PARTIAL, FAILED") val status: String,
        @field:Schema(description = "누적 실행 회차") val attempt: Int,
        @field:Schema(
            description = "사진 결과: PENDING, UPDATED, PRESERVED, NO_DATA, NO_SOURCE, FAILED",
        ) val photos: String,
        @field:Schema(description = "태그 재추출 결과") val tags: String,
        @field:Schema(description = "상태 최종 변경 시각") val updatedAt: Instant,
    ) {
        companion object {
            fun from(job: PlaceReprocessingJob) = JobResponse(
                job.placeId,
                job.status.name,
                job.attempt,
                job.photos.name,
                job.tags.name,
                job.updatedAt,
            )
        }
    }
}
