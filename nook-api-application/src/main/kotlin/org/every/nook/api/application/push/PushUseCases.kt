package org.every.nook.api.application.push

import mu.KotlinLogging

class RegisterPushTokenUseCase(private val pushTokenPort: PushTokenPort) {
    operator fun invoke(command: Command) {
        pushTokenPort.register(command.userId, command.token, command.platform)
    }

    data class Command(val userId: Long, val token: String, val platform: PushPlatform)
}

class DeletePushTokenUseCase(private val pushTokenPort: PushTokenPort) {
    operator fun invoke(command: Command) {
        pushTokenPort.delete(command.userId, command.token)
    }

    data class Command(val userId: Long, val token: String)
}

class SendPostProcessingPushUseCase(
    private val pushTokenPort: PushTokenPort,
    private val sender: PushNotificationSender,
) {
    operator fun invoke(command: Command): Result {
        val tokens = pushTokenPort.findEnabledTokensByPostId(command.postId)
        if (tokens.isEmpty()) {
            return Result(0, 0, 0)
        }

        val message = when (command.outcome) {
            Outcome.COMPLETED -> PushMessage(
                title = "게시물 저장이 완료됐어요!",
                body = "지금 앱에서 확인해보세요.",
                data = mapOf(
                    TYPE_KEY to TYPE_POST_PROCESSING,
                    OUTCOME_KEY to OUTCOME_COMPLETED,
                    POST_ID_KEY to command.postId.toString(),
                ),
            )

            Outcome.FAILED -> PushMessage(
                title = "앗, 저장에 실패했어요",
                body = "다시 시도하러 가볼까요?",
                data = mapOf(
                    TYPE_KEY to TYPE_POST_PROCESSING,
                    OUTCOME_KEY to OUTCOME_FAILED,
                    POST_ID_KEY to command.postId.toString(),
                ),
            )
        }

        val sendResult = sender.send(tokens.map(PushToken::token).distinct(), message)
        if (sendResult.invalidTokens.isNotEmpty()) {
            pushTokenPort.disable(sendResult.invalidTokens, INVALID_TOKEN_REASON)
        }
        logger.info {
            "Post processing push sent: postId=${command.postId}, outcome=${command.outcome}, " +
                "successCount=${sendResult.successCount}, failureCount=${sendResult.failureCount}, " +
                "invalidTokenCount=${sendResult.invalidTokens.size}"
        }
        return Result(
            targetCount = tokens.size,
            successCount = sendResult.successCount,
            failureCount = sendResult.failureCount,
        )
    }

    data class Command(val postId: Long, val outcome: Outcome)

    enum class Outcome {
        COMPLETED,
        FAILED,
    }

    data class Result(val targetCount: Int, val successCount: Int, val failureCount: Int)

    private companion object {
        val logger = KotlinLogging.logger {}
        const val TYPE_KEY = "type"
        const val TYPE_POST_PROCESSING = "POST_PROCESSING"
        const val OUTCOME_KEY = "outcome"
        const val OUTCOME_COMPLETED = "COMPLETED"
        const val OUTCOME_FAILED = "FAILED"
        const val POST_ID_KEY = "postId"
        const val INVALID_TOKEN_REASON = "FCM token is invalid or unregistered"
    }
}
