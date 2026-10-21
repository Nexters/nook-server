package org.every.nook.api.application.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `gets and updates a push preference`() {
        val port = RecordingPushPreferencePort()

        val initial = GetPushPreferenceUseCase(port)(GetPushPreferenceUseCase.Query(userId = 7))
        val updated = UpdatePushPreferenceUseCase(port)(
            UpdatePushPreferenceUseCase.Command(userId = 7, postProcessingEnabled = false),
        )

        assertTrue(initial.postProcessingEnabled)
        assertEquals(PushPreference(false), updated)
        assertEquals(false, port.preferences[7])
    }

    @Test
    fun `sends completed push and disables invalid tokens`() {
        val port = RecordingPushTokenPort(
            tokens = listOf(
                PostProcessingPushTarget(7, 71, "token-1"),
                PostProcessingPushTarget(7, 71, "token-2"),
            ),
        )
        val preferencePort = RecordingPushPreferencePort()
        val sender = RecordingPushNotificationSender(invalidTokens = listOf("token-2"))

        val result = SendPostProcessingPushUseCase(port, preferencePort, sender)(
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
        assertEquals("71", sender.message?.data?.get("savedPostId"))
        assertEquals(listOf("token-2"), port.disabledTokens)
        assertEquals(SendPostProcessingPushUseCase.Result(2, 1, 1), result)
    }

    @Test
    fun `sends failed push`() {
        val port = RecordingPushTokenPort(tokens = listOf(PostProcessingPushTarget(7, 71, "token-1")))
        val sender = RecordingPushNotificationSender()

        SendPostProcessingPushUseCase(port, RecordingPushPreferencePort(), sender)(
            SendPostProcessingPushUseCase.Command(
                postId = 11,
                outcome = SendPostProcessingPushUseCase.Outcome.FAILED,
            ),
        )

        assertEquals("앗, 저장에 실패했어요", sender.message?.title)
        assertEquals("다시 시도하러 가볼까요?", sender.message?.body)
        assertEquals("FAILED", sender.message?.data?.get("outcome"))
        assertEquals("71", sender.message?.data?.get("savedPostId"))
    }

    @Test
    fun `does not send to users who disabled post processing push`() {
        val port = RecordingPushTokenPort(
            tokens = listOf(
                PostProcessingPushTarget(7, 71, "token-disabled"),
                PostProcessingPushTarget(8, 81, "token-enabled"),
            ),
        )
        val preferencePort = RecordingPushPreferencePort(mutableMapOf(7L to false))
        val sender = RecordingPushNotificationSender()

        val result = SendPostProcessingPushUseCase(port, preferencePort, sender)(
            SendPostProcessingPushUseCase.Command(11, SendPostProcessingPushUseCase.Outcome.COMPLETED),
        )

        assertEquals(listOf("token-enabled"), sender.tokens)
        assertEquals(SendPostProcessingPushUseCase.Result(1, 1, 0), result)
    }

    @Test
    fun `sends each user their saved post id across multiple devices for both outcomes`() {
        for (outcome in SendPostProcessingPushUseCase.Outcome.entries) {
            val port = RecordingPushTokenPort(
                listOf(
                    PostProcessingPushTarget(7, 71, "user-7-phone"),
                    PostProcessingPushTarget(8, 81, "user-8-phone"),
                    PostProcessingPushTarget(7, 71, "user-7-tablet"),
                ),
            )
            val sender = RecordingPushNotificationSender(listOf("user-7-tablet"))
            val result = SendPostProcessingPushUseCase(port, RecordingPushPreferencePort(), sender)(
                SendPostProcessingPushUseCase.Command(11, outcome),
            )
            assertEquals(2, sender.calls.size)
            val bySavedId = sender.calls.associate { (tokens, message) -> message.data["savedPostId"] to tokens }
            assertEquals(listOf("user-7-phone", "user-7-tablet"), bySavedId["71"])
            assertEquals(listOf("user-8-phone"), bySavedId["81"])
            sender.calls.forEach { (_, message) ->
                assertEquals("11", message.data["postId"])
                assertEquals(outcome.name, message.data["outcome"])
                assertEquals("POST_PROCESSING", message.data["type"])
            }
            assertEquals(SendPostProcessingPushUseCase.Result(3, 2, 1), result)
            assertEquals(listOf("user-7-tablet"), port.disabledTokens)
        }
    }

    @Test
    fun `does not send without an eligible saved post or when all preferences are disabled`() {
        for (targets in listOf(emptyList(), listOf(PostProcessingPushTarget(7, 71, "token")))) {
            val sender = RecordingPushNotificationSender()
            val result = SendPostProcessingPushUseCase(
                RecordingPushTokenPort(targets),
                RecordingPushPreferencePort(mutableMapOf(7L to false)),
                sender,
            )(SendPostProcessingPushUseCase.Command(11, SendPostProcessingPushUseCase.Outcome.COMPLETED))
            assertEquals(SendPostProcessingPushUseCase.Result(0, 0, 0), result)
            assertTrue(sender.calls.isEmpty())
        }
    }

    private data class RegisterCall(val userId: Long, val token: String, val platform: PushPlatform)

    private class RecordingPushTokenPort(private val tokens: List<PostProcessingPushTarget> = emptyList()) :
        PushTokenPort {
        val registerCalls = mutableListOf<RegisterCall>()
        val deleteCalls = mutableListOf<Pair<Long, String>>()
        val disabledTokens = mutableListOf<String>()

        override fun register(userId: Long, token: String, platform: PushPlatform) {
            registerCalls += RegisterCall(userId, token, platform)
        }

        override fun delete(userId: Long, token: String) {
            deleteCalls += userId to token
        }

        override fun findPostProcessingTargets(postId: Long): List<PostProcessingPushTarget> = tokens

        override fun disable(tokens: Collection<String>, reason: String) {
            disabledTokens += tokens
        }
    }

    private class RecordingPushNotificationSender(private val invalidTokens: List<String> = emptyList()) :
        PushNotificationSender {
        val calls = mutableListOf<Pair<List<String>, PushMessage>>()
        var tokens: List<String> = emptyList()
            private set
        var message: PushMessage? = null
            private set

        override fun send(tokens: List<String>, message: PushMessage): PushSendResult {
            this.tokens = tokens
            this.message = message
            calls += tokens to message
            val invalidForCall = invalidTokens.filter { it in tokens }
            return PushSendResult(
                successCount = tokens.size - invalidForCall.size,
                failureCount = invalidForCall.size,
                invalidTokens = invalidForCall,
            )
        }
    }

    private class RecordingPushPreferencePort(val preferences: MutableMap<Long, Boolean> = mutableMapOf()) :
        PushPreferencePort {
        override fun get(userId: Long): PushPreference = PushPreference(preferences[userId] ?: true)

        override fun update(userId: Long, postProcessingEnabled: Boolean): PushPreference {
            preferences[userId] = postProcessingEnabled
            return PushPreference(postProcessingEnabled)
        }

        override fun findPostProcessingDisabledUserIds(userIds: Collection<Long>): Set<Long> =
            userIds.filterTo(mutableSetOf()) { preferences[it] == false }
    }
}
