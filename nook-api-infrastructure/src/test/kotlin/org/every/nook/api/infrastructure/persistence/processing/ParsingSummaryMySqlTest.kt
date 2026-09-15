package org.every.nook.api.infrastructure.persistence.processing

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ParsingSummaryMySqlTest {
    private lateinit var db: ParsingMySqlFixture
    private val events = ListAppender<ILoggingEvent>()
    private val logger = LoggerFactory.getLogger(ParsingSummaryAdapter::class.java) as Logger

    @BeforeAll
    fun start() {
        db = ParsingMySqlFixture()
        events.start()
        logger.addAppender(events)
    }

    @AfterAll
    fun stop() {
        logger.detachAppender(events)
        if (::db.isInitialized) db.close()
    }

    @BeforeTest
    fun reset() {
        val tables = listOf(
            "post_places",
            "posts",
            "parsing_follow_up_jobs",
            "post_content_parsing_jobs",
            "place_parsing_jobs",
        )
        tables.forEach {
            db.jdbc.update("DELETE FROM $it")
        }
        db.jdbc.update("INSERT INTO posts (id, updated_at) VALUES (1, '2026-01-01 00:00:00')")
        events.list.clear()
    }

    @Test
    fun `waits for final success and publishes grouped failure once without changing post timestamp`() {
        insert("FAILED")
        insert("PROCESSING")
        val publisher = db.summaries()
        assertEquals(listOf(1L), publisher.candidates(0, 100))
        assertEquals(emptyList(), publisher.candidates(1, 100))
        publisher.publish(1)
        assertEquals(0, events.list.size)
        db.jdbc.update("UPDATE parsing_follow_up_jobs SET status = 'COMPLETED' WHERE status = 'PROCESSING'")
        publisher.publish(1)
        publisher.publish(1)
        assertEquals(1, events.list.size)
        val fields = events.list.single().keyValuePairs.associate { it.key to it.value }
        assertEquals(1, fields["failed_count"])
        assertEquals(1, fields["completed_count"])
        assertFalse(fields["failure_summary"].toString().contains("secret.example"))
        assertEquals(
            "2026-01-01 00:00:00",
            db.jdbc.queryForObject(
                "SELECT DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') FROM posts",
                String::class.java,
            ),
        )
    }

    @Test
    fun `concurrent publishers and a new publisher instance do not duplicate summaries`() {
        repeat(6) { insert("FAILED") }
        val barrier = CyclicBarrier(2)
        Executors.newFixedThreadPool(2).use { pool ->
            val futures = (1..2).map {
                pool.submit(
                    Callable {
                        barrier.await(10, TimeUnit.SECONDS)
                        db.summaries().publish(1)
                    },
                )
            }
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
        }
        db.summaries().publish(1)
        assertEquals(1, events.list.size)
        val fields = events.list.single().keyValuePairs.associate { it.key to it.value }
        assertEquals(6, fields["failed_count"])
    }

    @Test
    fun `manual retry waits then emits a new summary only if failure remains`() {
        insert("FAILED")
        val publisher = db.summaries()
        publisher.publish(1)
        val before = fingerprint()
        db.jdbc.update("UPDATE parsing_follow_up_jobs SET status = 'PENDING'")
        publisher.publish(1)
        assertEquals(1, events.list.size)
        db.jdbc.update("UPDATE parsing_follow_up_jobs SET status = 'FAILED', attempt_count = attempt_count + 1")
        publisher.publish(1)
        assertEquals(2, events.list.size)
        assertNotEquals(before, fingerprint())
        db.jdbc.update("UPDATE parsing_follow_up_jobs SET status = 'COMPLETED', attempt_count = attempt_count + 1")
        publisher.publish(1)
        assertEquals(2, events.list.size)
    }

    @Test
    fun `summary carries precise error code detail and administrator retry outcome`() {
        insert("FAILED")
        db.jdbc.update(
            """
            UPDATE parsing_follow_up_jobs SET attempt_count=8,retry_attempt_count=4,
                failure_reason='HTTP 403: image access denied token=secret-token'
            """.trimIndent(),
        )
        db.summaries().publish(1)
        val fields = events.list.single().keyValuePairs.associate { it.key to it.value }
        val summary = fields["failure_summary"].toString()
        assertTrue(summary.contains("image access denied"))
        assertTrue(summary.contains("\"code\":\"403\""))
        assertTrue(summary.contains("\"attempts\":8"))
        assertTrue(summary.contains("\"retried\":true"))
        assertFalse(summary.contains("secret-token"))
    }

    @Test
    fun `historical failures without timestamps and all success do not alert`() {
        insert("FAILED")
        db.jdbc.update("UPDATE parsing_follow_up_jobs SET last_failed_at = NULL")
        db.summaries().publish(1)
        assertEquals(emptyList(), db.summaries().candidates(0, 100))
        db.jdbc.update("UPDATE parsing_follow_up_jobs SET status = 'COMPLETED'")
        db.summaries().publish(1)
        assertEquals(0, events.list.size)
    }

    @Test
    fun `content and place failures join media only after every stage terminates`() {
        insert("FAILED")
        db.jdbc.update(
            """
            INSERT INTO post_content_parsing_jobs
                (post_id, status, attempt_count, retry_attempt_count, next_attempt_at, progress_percent,
                 last_failed_at, last_failure_stage, failure_reason)
            VALUES (1, 'COMPLETED', 1, 1, NOW(6), 45, NULL, NULL, NULL)
            """.trimIndent(),
        )
        db.jdbc.update(
            """
            INSERT INTO place_parsing_jobs
                (post_id, status, attempt_count, retry_attempt_count, next_attempt_at, progress_percent)
            VALUES (1, 'PENDING', 1, 1, NOW(6), 50)
            """.trimIndent(),
        )
        db.summaries().publish(1)
        assertEquals(0, events.list.size)
        db.jdbc.update(
            """
            UPDATE place_parsing_jobs SET status = 'FAILED', last_failed_at = NOW(6),
                last_failure_stage = 'PLACE_IMAGE_OCR', failure_reason = 'OCR failed'
            """.trimIndent(),
        )
        db.summaries().publish(1)
        val fields = events.list.single().keyValuePairs.associate { it.key to it.value }
        assertEquals(2, fields["failed_count"])
        assertEquals(3, fields["total_count"])
    }

    @Test
    fun `rollback emits nothing and preserves eligibility for the next collector`() {
        insert("FAILED")
        assertFailsWith<IllegalStateException> {
            db.summaryTransaction {
                db.summaries().publish(1)
                assertEquals(0, events.list.size)
                error("rollback")
            }
        }
        assertNull(fingerprint())
        assertEquals(0, events.list.size)
        db.summaries().publish(1)
        assertEquals(1, events.list.size)
    }

    @Test
    fun `zero places waits for other jobs then warns once across concurrent publishers`() {
        insertZeroPlaces()
        insert("PROCESSING")
        val publisher = db.summaries()
        assertEquals(listOf(1L), publisher.candidates(0, 100))
        publisher.publish(1)
        assertEquals(0, events.list.size)
        db.jdbc.update("UPDATE parsing_follow_up_jobs SET status='COMPLETED'")
        val barrier = CyclicBarrier(2)
        Executors.newFixedThreadPool(2).use { pool ->
            val futures = (1..2).map {
                pool.submit(
                    Callable {
                        barrier.await(10, TimeUnit.SECONDS)
                        db.summaries().publish(1)
                    },
                )
            }
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
        }
        publisher.publish(1)
        val event = events.list.single()
        val fields = event.keyValuePairs.associate { it.key to it.value }
        assertEquals("WARN", event.level.toString())
        assertEquals("post.parsing.summary_warning", fields["event_type"])
        assertEquals("NO_PLACES", fields["warning_code"])
        assertEquals(0, fields["failed_count"])
        assertEquals(2, fields["completed_count"])
        assertNull(fields["failed_at"])
    }

    @Test
    fun `zero places and actual failure share one error summary`() {
        insertZeroPlaces()
        insert("FAILED")
        db.summaries().publish(1)
        db.summaries().publish(1)
        val event = events.list.single()
        val fields = event.keyValuePairs.associate { it.key to it.value }
        assertEquals("ERROR", event.level.toString())
        assertEquals("post.parsing.summary_failed", fields["event_type"])
        assertEquals("NO_PLACES", fields["warning_code"])
        assertEquals(1, fields["failed_count"])
        assertEquals(1, fields["completed_count"])
    }

    @Test
    fun `saved mapping and unknown or unfinished place result do not warn`() {
        insertZeroPlaces()
        db.jdbc.update("INSERT INTO post_places (post_id,place_id) VALUES (1,10)")
        db.summaries().publish(1)
        assertEquals(emptyList(), db.summaries().candidates(0, 100))
        db.jdbc.update("DELETE FROM post_places")
        db.jdbc.update("UPDATE place_parsing_jobs SET resolved_place_count=NULL")
        db.summaries().publish(1)
        assertEquals(emptyList(), db.summaries().candidates(0, 100))
        db.jdbc.update("UPDATE place_parsing_jobs SET resolved_place_count=0,status='PROCESSING'")
        db.summaries().publish(1)
        assertEquals(0, events.list.size)
    }

    @Test
    fun `zero place warning rolls back and retries only after a new completed attempt`() {
        insertZeroPlaces()
        assertFailsWith<IllegalStateException> {
            db.summaryTransaction {
                db.summaries().publish(1)
                error("rollback")
            }
        }
        assertNull(fingerprint())
        assertEquals(0, events.list.size)
        db.summaries().publish(1)
        db.jdbc.update("UPDATE place_parsing_jobs SET status='PENDING',attempt_count=2")
        db.summaries().publish(1)
        assertEquals(1, events.list.size)
        db.jdbc.update("UPDATE place_parsing_jobs SET status='COMPLETED'")
        db.summaries().publish(1)
        db.summaries().publish(1)
        assertEquals(2, events.list.size)
    }

    private fun insertZeroPlaces() {
        db.jdbc.update(
            """
            INSERT INTO place_parsing_jobs
                (post_id,status,attempt_count,retry_attempt_count,next_attempt_at,progress_percent,resolved_place_count)
            VALUES (1,'COMPLETED',1,1,NOW(6),100,0)
            """.trimIndent(),
        )
    }

    private fun insert(status: String) {
        db.insertMediaJob()
        db.jdbc.update(
            """
            UPDATE parsing_follow_up_jobs SET status = ?, attempt_count = 4,
                last_failed_at = '2026-09-13 00:00:00', failure_reason = 'download https://secret.example/token'
            WHERE status = 'PENDING'
            """.trimIndent(),
            status,
        )
    }

    private fun fingerprint(): String? = db.jdbc.queryForObject(
        "SELECT parsing_alert_fingerprint FROM posts WHERE id = 1",
        String::class.java,
    )
}
