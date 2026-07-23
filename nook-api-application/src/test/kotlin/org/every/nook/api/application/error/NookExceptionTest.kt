package org.every.nook.api.application.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NookExceptionTest {
    @Test
    fun `uses the default reason from its error code`() {
        val exception = TestNookException()

        assertEquals("TEST_ERROR", exception.errorCode.code)
        assertEquals("Test error.", exception.reason)
        assertEquals(ErrorType.CONFLICT, exception.errorCode.type)
        assertNull(exception.data)
    }

    private class TestNookException : NookException(TestErrorCode)

    private data object TestErrorCode : NookErrorCode {
        override val code: String = "TEST_ERROR"
        override val defaultReason: String = "Test error."
        override val type: ErrorType = ErrorType.CONFLICT
    }
}
