package org.every.nook.api.application.admin

data class RefetchPostContentCommand(val postId: Long, val actor: AdminActor, val reason: String)

fun interface AdminPostContentRefetchPort {
    fun refetch(command: RefetchPostContentCommand): AdminPostRecovery
}

class RefetchAdminPostContentUseCase(private val port: AdminPostContentRefetchPort) {
    operator fun invoke(command: RefetchPostContentCommand): AdminPostRecovery {
        require(command.postId > 0)
        require(command.reason.isNotBlank() && command.reason.length <= MAX_REASON_LENGTH)
        return port.refetch(command.copy(reason = command.reason.trim()))
    }
}

private const val MAX_REASON_LENGTH = 500
