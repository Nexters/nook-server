package org.every.nook.api.application.admin

import java.time.Instant

data class PostProcessingQuery(
    val category: String = "INITIAL_FAILED",
    val failedSince: Instant? = null,
    val page: Int = 1,
    val limit: Int = 20,
    val postId: Long? = null,
)
data class PostProcessingPage(val posts: List<AdminPostRecovery>, val total: Long)
data class ChangePostDisposition(val postId: Long, val disposition: String, val reason: String, val actor: AdminActor)

interface AdminPostProcessingPort {
    fun list(query: PostProcessingQuery): PostProcessingPage
    fun change(command: ChangePostDisposition): AdminPostRecovery
}

class ListPostProcessingUseCase(private val port: AdminPostProcessingPort) {
    operator fun invoke(query: PostProcessingQuery): PostProcessingPage {
        require(
            query.category in setOf("ALL", "INITIAL_FAILED", "RETRY_FAILED", "UNPROCESSABLE", "ACTIVE", "COMPLETED"),
        )
        require(query.page in 1..MAX_PAGE && query.limit in 1..MAX_LIMIT)
        require(query.postId == null || query.postId > 0)
        return port.list(query)
    }
    private companion object {
        const val MAX_PAGE = 10000
        const val MAX_LIMIT = 100
    }
}

class ChangePostDispositionUseCase(private val port: AdminPostProcessingPort) {
    operator fun invoke(command: ChangePostDisposition): AdminPostRecovery {
        require(command.postId > 0)
        require(command.disposition in setOf("OPEN", "UNPROCESSABLE"))
        require(command.reason.isNotBlank() && command.reason.length <= MAX_REASON_LENGTH)
        return port.change(command.copy(reason = command.reason.trim()))
    }
    private companion object {
        const val MAX_REASON_LENGTH = 500
    }
}
