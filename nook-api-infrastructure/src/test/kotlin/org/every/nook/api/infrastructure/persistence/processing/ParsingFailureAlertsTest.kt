package org.every.nook.api.infrastructure.persistence.processing

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ParsingFailureAlertsTest {
    @Test
    fun `terminal alert is emitted only on commit and includes structured stage`() {
        val logger = LoggerFactory.getLogger(ParsingFailureAlerts::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            TransactionSynchronizationManager.initSynchronization()
            ParsingFailureAlerts().afterCommit(616, "POST_MEDIA", 378, 4, "POST_MEDIA", Instant.EPOCH)
            assertEquals(0, appender.list.size)
            val callbacks = TransactionSynchronizationManager.getSynchronizations()
            callbacks.forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }
            assertEquals(0, appender.list.size)
            TransactionSynchronizationManager.clearSynchronization()
            TransactionSynchronizationManager.initSynchronization()
            ParsingFailureAlerts().afterCommit(616, "POST_MEDIA", 378, 4, "POST_MEDIA", Instant.EPOCH)
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
            assertEquals(1, appender.list.size)
            val values = appender.list.single().keyValuePairs.associate { it.key to it.value }
            assertEquals("post.parsing.failed", values["event_type"])
            assertEquals(616L, values["post_id"])
            assertEquals("POST_MEDIA", values["failure_stage"])
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
            logger.detachAppender(appender)
        }
    }
}
