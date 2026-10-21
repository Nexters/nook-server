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

class GetPushPreferenceUseCase(private val pushPreferencePort: PushPreferencePort) {
    operator fun invoke(query: Query): PushPreference = pushPreferencePort.get(query.userId)

    data class Query(val userId: Long)
}

class UpdatePushPreferenceUseCase(private val pushPreferencePort: PushPreferencePort) {
    operator fun invoke(command: Command): PushPreference =
        pushPreferencePort.update(command.userId, command.postProcessingEnabled)

    data class Command(val userId: Long, val postProcessingEnabled: Boolean)
}

class SendPostProcessingPushUseCase(
    private val pushTokenPort: PushTokenPort,
    private val pushPreferencePort: PushPreferencePort,
    private val sender: PushNotificationSender,
) {
    operator fun invoke(command: Command): Result {
        val targets = pushTokenPort.findPostProcessingTargets(command.postId)
        val disabledUserIds = pushPreferencePort.findPostProcessingDisabledUserIds(
            targets.map(PostProcessingPushTarget::userId).distinct(),
        )
        val enabledTargets = targets.filterNot { it.userId in disabledUserIds }
        val results = enabledTargets.groupBy { it.userId to it.savedPostId }.map { (_, userTargets) ->
            val tokens = userTargets.map(PostProcessingPushTarget::token).distinct()
            sender.send(tokens, command.message(userTargets.first().savedPostId))
        }
        val invalidTokens = results.flatMap(PushSendResult::invalidTokens).distinct()
        if (invalidTokens.isNotEmpty()) {
            pushTokenPort.disable(invalidTokens, INVALID_TOKEN_REASON)
        }
        val result = Result(
            targetCount = enabledTargets.map(PostProcessingPushTarget::token).distinct().size,
            successCount = results.sumOf(PushSendResult::successCount),
            failureCount = results.sumOf(PushSendResult::failureCount),
        )
        logger.info {
            "Post processing push sent: postId=${command.postId}, outcome=${command.outcome}, " +
                "successCount=${result.successCount}, failureCount=${result.failureCount}, " +
                "invalidTokenCount=${invalidTokens.size}"
        }
        return result
    }

    private fun Command.message(savedPostId: Long): PushMessage = PushMessage(
        title = when (outcome) {
            Outcome.COMPLETED -> "게시물 저장이 완료됐어요!"
            Outcome.FAILED -> "앗, 저장에 실패했어요"
        },
        body = when (outcome) {
            Outcome.COMPLETED -> "지금 앱에서 확인해보세요."
            Outcome.FAILED -> "다시 시도하러 가볼까요?"
        },
        data = mapOf(
            TYPE_KEY to TYPE_POST_PROCESSING,
            OUTCOME_KEY to outcome.name,
            POST_ID_KEY to postId.toString(),
            SAVED_POST_ID_KEY to savedPostId.toString(),
        ),
    )

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
        const val POST_ID_KEY = "postId"
        const val SAVED_POST_ID_KEY = "savedPostId"
        const val INVALID_TOKEN_REASON = "FCM token is invalid or unregistered"
    }
}
