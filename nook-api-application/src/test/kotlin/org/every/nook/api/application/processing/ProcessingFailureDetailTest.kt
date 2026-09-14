package org.every.nook.api.application.processing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProcessingFailureDetailTest {
    @Test
    fun `explains known status and keeps actionable detail without credentials`() {
        val failure = processingFailureDetail(
            "download HTTP 403: access denied https://cdn.example/image?token=secret Bearer abc.def token=private",
        )
        assertEquals("403", failure.code)
        assertTrue("인증" in failure.summary)
        assertTrue("access denied" in failure.detail)
        listOf("cdn.example", "abc.def", "private", "token=secret").forEach {
            assertFalse(it in failure.detail)
        }
    }

    @Test
    fun `redacts quoted credentials and does not invent reasons for unknown failures`() {
        val result = processingFailureDetail("Unexpected response: \"apiKey\": \"two secret words\"")
        assertFalse("two secret words" in result.detail)
        assertEquals(null, result.code)
        assertEquals("처리 중 오류가 발생했습니다", result.summary)
    }
}
