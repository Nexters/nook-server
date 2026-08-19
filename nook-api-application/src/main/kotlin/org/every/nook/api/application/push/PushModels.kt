package org.every.nook.api.application.push

enum class PushPlatform {
    IOS,
    ANDROID,
}

data class PushToken(val token: String, val platform: PushPlatform)

data class PushMessage(val title: String, val body: String, val data: Map<String, String> = emptyMap())

data class PushSendResult(val successCount: Int, val failureCount: Int, val invalidTokens: List<String>)
