package org.every.nook.api.infrastructure.push

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import mu.KotlinLogging
import org.every.nook.api.application.push.PushMessage
import org.every.nook.api.application.push.PushNotificationSender
import org.every.nook.api.application.push.PushSendResult
import org.springframework.stereotype.Component

@Component
class FirebasePushNotificationSender : PushNotificationSender {
    private val messaging: FirebaseMessaging by lazy {
        FirebaseMessaging.getInstance(FirebaseApp.getApps().firstOrNull() ?: FirebaseApp.initializeApp())
    }

    override fun send(tokens: List<String>, message: PushMessage): PushSendResult {
        val distinctTokens = tokens.distinct()
        if (distinctTokens.isEmpty()) {
            return PushSendResult(0, 0, emptyList())
        }

        val messages = distinctTokens.map { token ->
            Message.builder()
                .setToken(token)
                .setNotification(
                    Notification.builder()
                        .setTitle(message.title)
                        .setBody(message.body)
                        .build(),
                )
                .putAllData(message.data)
                .build()
        }
        val response = messaging.sendEach(messages)
        val invalidTokens = response.responses.mapIndexedNotNull { index, sendResponse ->
            val exception = sendResponse.exception as? FirebaseMessagingException
            if (!sendResponse.isSuccessful && exception?.messagingErrorCode.isInvalidTokenError()) {
                distinctTokens[index]
            } else {
                null
            }
        }
        if (response.failureCount > 0) {
            logger.warn {
                "FCM push send completed with failures: successCount=${response.successCount}, " +
                    "failureCount=${response.failureCount}, invalidTokenCount=${invalidTokens.size}"
            }
        }
        return PushSendResult(
            successCount = response.successCount,
            failureCount = response.failureCount,
            invalidTokens = invalidTokens,
        )
    }

    private fun MessagingErrorCode?.isInvalidTokenError(): Boolean =
        this == MessagingErrorCode.UNREGISTERED || this == MessagingErrorCode.INVALID_ARGUMENT

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
