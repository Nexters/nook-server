package org.every.nook.api.application.push

import kotlin.test.Test
import kotlin.test.assertEquals

class PushUseCasesTest {
    @Test
    fun `registers a push token`() {
        val port = RecordingPushTokenPort()
        RegisterPushTokenUseCase(port)(
            RegisterPushTokenUseCase.Command(
                userId = 7,
                token = "token-1",
                platform = PushPlatform.IOS,
            ),
        )

        assertEquals(RegisterCall(7, "token-1", PushPlatform.IOS), port.registerCalls.single())
    }

    @Test
    fun `deletes a push token`() {
        val port = RecordingPushTokenPort()
        DeletePushTokenUseCase(port)(DeletePushTokenUseCase.Command(userId = 7, token = "token-1"))

        assertEquals(7L to "token-1", port.deleteCalls.single())
    }

    @Test
    fun `sends completed push and disables invalid tokens`() {
        val port = RecordingPushTokenPort(
            tokens = listOf(
                PushToken("token-1", PushPlatform.IOS),
                PushToken("token-2", PushPlatform.IOS),
            ),
        )
        val sender = RecordingPushNotificationSender(invalidTokens = listOf("token-2"))

        val result = SendPostProcessingPushUseCase(port, sender)(
            SendPostProcessingPushUseCase.Command(
                postId = 11,
                outcome = SendPostProcessingPushUseCase.Outcome.COMPLETED,
            ),
        )

        assertEquals(listOf("token-1", "token-2"), sender.tokens)
        assertEquals("게시물 저장이 완료됐어요!", sender.message?.title)
        assertEquals("지금 앱에서 확인해보세요.", sender.message?.body)
        assertEquals("COMPLETED", sender.message?.data?.get("outcome"))
        assertEquals("11", sender.message?.data?.get("postId"))
        assertEquals(listOf("token-2"), port.disabledTokens)
        assertEquals(SendPostProcessingPushUseCase.Result(2, 1, 1), result)
    }

    @Test
    fun `sends failed push`() {
        val port = RecordingPushTokenPort(tokens = listOf(PushToken("token-1", PushPlatform.IOS)))
        val sender = RecordingPushNotificationSender()

        SendPostProcessingPushUseCase(port, sender)(
            SendPostProcessingPushUseCase.Command(
                postId = 11,
                outcome = SendPostProcessingPushUseCase.Outcome.FAILED,
            ),
        )

        assertEquals("앗, 저장에 실패했어요", sender.message?.title)
        assertEquals("다시 시도하러 가볼까요?", sender.message?.body)
        assertEquals("FAILED", sender.message?.data?.get("outcome"))
    }

    private data class RegisterCall(val userId: Long, val token: String, val platform: PushPlatform)

    private class RecordingPushTokenPort(private val tokens: List<PushToken> = emptyList()) : PushTokenPort {
        val registerCalls = mutableListOf<RegisterCall>()
        val deleteCalls = mutableListOf<Pair<Long, String>>()
        val disabledTokens = mutableListOf<String>()

        override fun register(userId: Long, token: String, platform: PushPlatform) {
            registerCalls += RegisterCall(userId, token, platform)
        }

        override fun delete(userId: Long, token: String) {
            deleteCalls += userId to token
        }

        override fun findEnabledTokensByPostId(postId: Long): List<PushToken> = tokens

        override fun disable(tokens: Collection<String>, reason: String) {
            disabledTokens += tokens
        }
    }

    private class RecordingPushNotificationSender(private val invalidTokens: List<String> = emptyList()) :
        PushNotificationSender {
        var tokens: List<String> = emptyList()
            private set
        var message: PushMessage? = null
            private set

        override fun send(tokens: List<String>, message: PushMessage): PushSendResult {
            this.tokens = tokens
            this.message = message
            return PushSendResult(
                successCount = tokens.size - invalidTokens.size,
                failureCount = invalidTokens.size,
                invalidTokens = invalidTokens,
            )
        }
    }
}
