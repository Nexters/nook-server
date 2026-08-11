package org.every.nook.api.logging

import org.slf4j.MDC
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MdcTaskDecoratorTest {
    @AfterTest
    fun clearMdc() {
        MDC.clear()
    }

    @Test
    fun `propagates caller MDC to decorated task and restores previous context`() {
        MDC.put(RequestLoggingFields.REQUEST_ID, "req-test-1")
        val decorated = MdcTaskDecorator().decorate {
            assertEquals("req-test-1", MDC.get(RequestLoggingFields.REQUEST_ID))
            MDC.put(RequestLoggingFields.REQUEST_ID, "worker-override")
        }

        MDC.clear()
        MDC.put(RequestLoggingFields.REQUEST_ID, "worker-before")

        decorated.run()

        assertEquals("worker-before", MDC.get(RequestLoggingFields.REQUEST_ID))
    }

    @Test
    fun `clears task MDC when caller has no context`() {
        val decorated = MdcTaskDecorator().decorate {
            assertNull(MDC.get(RequestLoggingFields.REQUEST_ID))
        }

        MDC.put(RequestLoggingFields.REQUEST_ID, "worker-before")

        decorated.run()

        assertEquals("worker-before", MDC.get(RequestLoggingFields.REQUEST_ID))
    }
}
