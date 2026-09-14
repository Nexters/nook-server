package org.every.nook.api.application.admin

data class AdminPostRecovery(
    val postId: Long,
    val jobs: List<AdminFollowUpJob>,
    val title: String? = null,
    val disposition: String = "OPEN",
    val dispositionReason: String? = null,
    val dispositionChangedAt: java.time.Instant? = null,
)
data class AdminPostRecoveryPage(val posts: List<AdminPostRecovery>, val hasNext: Boolean)
data class RetryPostParsingCommand(val postId: Long, val actor: AdminActor, val reason: String)

interface AdminPostRecoveryPort {
    fun list(postId: Long?, status: String?, beforePostId: Long?, limit: Int): AdminPostRecoveryPage
    fun find(postId: Long): AdminPostRecovery?
    fun retry(command: RetryPostParsingCommand): AdminPostRecovery
}

class ListAdminPostRecoveryUseCase(private val port: AdminPostRecoveryPort) {
    operator fun invoke(postId: Long?, status: String?, beforePostId: Long?, limit: Int): AdminPostRecoveryPage {
        require(postId == null || postId > 0)
        require(beforePostId == null || beforePostId > 0)
        require(limit in 1..MAX_PAGE_SIZE)
        require(status == null || status in setOf("PENDING", "PROCESSING", "COMPLETED", "FAILED"))
        return port.list(postId, status, beforePostId, limit)
    }
}

class GetAdminPostRecoveryUseCase(private val port: AdminPostRecoveryPort) {
    operator fun invoke(postId: Long): AdminPostRecovery {
        require(postId > 0)
        return port.find(postId) ?: throw ParsingRecoveryException(ParsingRecoveryError.NOT_FOUND)
    }
}

class RetryAdminPostRecoveryUseCase(private val port: AdminPostRecoveryPort) {
    operator fun invoke(command: RetryPostParsingCommand): AdminPostRecovery {
        require(command.postId > 0)
        require(command.reason.isNotBlank() && command.reason.length <= MAX_REASON_LENGTH)
        return port.retry(command.copy(reason = command.reason.trim()))
    }
}

private const val MAX_PAGE_SIZE = 100
private const val MAX_REASON_LENGTH = 500
