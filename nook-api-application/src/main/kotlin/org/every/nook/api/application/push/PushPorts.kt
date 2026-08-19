package org.every.nook.api.application.push

interface PushTokenPort {
    fun register(userId: Long, token: String, platform: PushPlatform)

    fun delete(userId: Long, token: String)

    fun findEnabledTokensByPostId(postId: Long): List<PushToken>

    fun disable(tokens: Collection<String>, reason: String)
}

fun interface PushNotificationSender {
    fun send(tokens: List<String>, message: PushMessage): PushSendResult
}
