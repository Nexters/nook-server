package org.every.nook.api.admin

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.AdminFollowUpJob
import org.every.nook.api.application.admin.GetAdminFollowUpJobUseCase
import org.every.nook.api.application.admin.ListAdminFollowUpJobsUseCase
import org.every.nook.api.application.admin.RetryAdminFollowUpJobUseCase
import org.every.nook.api.application.admin.RetryParsingJobCommand
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/admin/v1/parsing-jobs")
@Tag(name = "Admin Parsing Recovery")
class AdminParsingRecoveryController(
    private val listJobs: ListAdminFollowUpJobsUseCase,
    private val retryJob: RetryAdminFollowUpJobUseCase,
    private val getJob: GetAdminFollowUpJobUseCase,
) {
    @GetMapping
    @Operation(summary = "후속 파싱 작업과 실패 원인을 조회합니다")
    fun list(
        @Parameter(description = "게시글 ID; 생략하면 전체 게시글")
        @RequestParam(required = false) postId: Long?,
        @Parameter(description = "PENDING, PROCESSING, COMPLETED, FAILED; 생략하면 전체 상태")
        @RequestParam(required = false) status: String?,
        @Parameter(description = "이 ID보다 이전 작업 조회")
        @RequestParam(required = false) beforeId: Long?,
        @Parameter(description = "조회 개수; 1~100")
        @RequestParam(defaultValue = "30") limit: Int,
    ) = listJobs(postId, status, beforeId, limit).let { page ->
        ApiResponse.success(JobsResponse(page.jobs.map(JobResponse::from), page.hasNext))
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "재시도한 후속 작업의 진행 상태를 확인합니다")
    fun get(@Parameter(description = "확인할 작업 ID") @PathVariable jobId: Long) =
        ApiResponse.success(JobResponse.from(getJob(jobId)))

    @PostMapping("/{jobId}/retry")
    @Operation(summary = "실패한 후속 작업만 다시 실행합니다")
    fun retry(
        @Parameter(description = "재시도할 실패 작업 ID") @PathVariable jobId: Long,
        @RequestBody request: RetryRequest,
        actor: AdminActor,
    ) = ApiResponse.success(JobResponse.from(retryJob(RetryParsingJobCommand(jobId, actor, request.reason, null))))

    data class JobsResponse(
        @field:Schema(description = "후속 작업 목록") val jobs: List<JobResponse>,
        @field:Schema(description = "다음 페이지 존재 여부") val hasNext: Boolean,
    )

    data class JobResponse(
        @field:Schema(description = "작업 ID") val id: Long,
        @field:Schema(description = "게시글 ID") val postId: Long,
        @field:Schema(description = "작업 종류") val type: String,
        @field:Schema(description = "처리 상태") val status: String,
        @field:Schema(description = "총 실행 횟수") val attempts: Int,
        @field:Schema(description = "최근 실패 사유") val failureReason: String?,
        @field:Schema(description = "다음 실행 가능 시각") val nextAttemptAt: Instant?,
        @field:Schema(description = "최종 변경 시각") val updatedAt: Instant,
        @field:Schema(description = "수동 재시도 상태: NONE, RETRY_PENDING, RETRY_PROCESSING, RECOVERED, RETRY_FAILED")
        val recoveryStatus: String,
        @field:Schema(description = "마지막 실제 실패 시각; 과거 미기록은 null") val lastFailedAt: Instant?,
        @field:Schema(description = "실패 단계") val failureStage: String?,
    ) {
        companion object {
            fun from(job: AdminFollowUpJob) = JobResponse(
                job.id,
                job.postId,
                job.type,
                job.status,
                job.attempts,
                job.failureReason,
                job.nextAttemptAt,
                job.updatedAt,
                job.recoveryStatus,
                job.lastFailedAt,
                job.failureStage,
            )
        }
    }

    data class RetryRequest(@field:Schema(description = "재처리 사유") val reason: String)
}
