package org.every.nook.api.infrastructure.persistence.processing

import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.admin.ParsingRecoveryException
import org.every.nook.api.application.admin.RetryParsingJobCommand
import org.every.nook.api.application.admin.RetryPostParsingCommand
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.PostContentParsingStatus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.PageRequest
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ParsingExecutionMySqlTest {
    private lateinit var db: ParsingMySqlFixture
    private val timeout = Duration.ofMinutes(5)

    @BeforeAll
    fun startDatabase() {
        db = ParsingMySqlFixture()
    }

    @AfterAll
    fun stopDatabase() {
        if (::db.isInitialized) db.close()
    }

    @BeforeTest
    fun cleanJobs() {
        db.jdbc.update("DELETE FROM parsing_follow_up_jobs")
        db.jdbc.update("DELETE FROM post_content_parsing_jobs")
        db.jdbc.update("DELETE FROM place_parsing_jobs")
    }

    @Test
    fun `two workers cannot claim the same active job`() {
        db.insertMediaJob()
        val barrier = CyclicBarrier(2)
        Executors.newFixedThreadPool(2).use { pool ->
            val futures = (1..2).map {
                pool.submit(
                    Callable {
                        barrier.await(WAIT_SECONDS, TimeUnit.SECONDS)
                        db.jobs.claim(1, timeout)
                    },
                )
            }
            assertEquals(1, futures.sumOf { it.get(WAIT_SECONDS, TimeUnit.SECONDS).size })
        }
    }

    @Test
    fun `expired execution cannot write results or change reclaimed job status`() {
        db.insertMediaJob()
        val old = db.jobs.claim(1, timeout).single()
        db.jdbc.update("UPDATE parsing_follow_up_jobs SET updated_at = '2026-01-01 00:00:00'")
        val current = db.jobs.claim(1, timeout).single()
        assertEquals(old.attempt + 1, current.attempt)
        assertFalse(db.jobs.writeResult(old.id, old.attempt) { error("stale result must not run") })
        assertFalse(db.jobs.complete(old.id, old.attempt))
        assertFalse(db.jobs.retry(old.id, old.attempt, ParsingMySqlFixture.NOW, "stale"))
        assertFalse(db.jobs.fail(old.id, old.attempt, "stale"))
        assertTrue(
            db.jobs.writeResult(current.id, current.attempt) {
                db.writeResultValue(current.id, "current")
            },
        )
        assertTrue(db.jobs.complete(current.id, current.attempt))
        assertEquals(
            "COMPLETED",
            db.jdbc.queryForObject("SELECT status FROM parsing_follow_up_jobs", String::class.java),
        )
    }

    @Test
    fun `result writes roll back together on failure`() {
        db.insertMediaJob()
        val job = db.jobs.claim(1, timeout).single()
        assertFailsWith<IllegalStateException> {
            db.jobs.writeResult(job.id, job.attempt) {
                db.writeResultValue(job.id, "partial")
                error("write failed")
            }
        }
        assertEquals(
            null,
            db.jdbc.queryForObject("SELECT failure_reason FROM parsing_follow_up_jobs", String::class.java),
        )
    }

    @Test
    fun `active jobs do not hide pending work behind query limit`() {
        listOf("post_content_parsing_jobs", "place_parsing_jobs").forEach { table ->
            db.jdbc.update(
                """
                INSERT INTO $table (post_id, status, attempt_count, next_attempt_at, progress_percent, updated_at)
                VALUES (1, 'PROCESSING', 1, '2026-01-01 00:00:00', 5, '2026-09-13 00:00:00'),
                       (2, 'PENDING', 0, '2026-09-12 00:00:00', 5, '2026-09-12 00:00:00')
                """.trimIndent(),
            )
        }
        val now = ParsingMySqlFixture.NOW
        assertEquals(
            listOf(2L),
            db.contentJobs.findAvailable(
                PostContentParsingStatus.PENDING,
                PostContentParsingStatus.PROCESSING,
                now,
                now.minus(timeout),
                PageRequest.of(0, 1),
            ).map { it.postId },
        )
        assertEquals(
            listOf(2L),
            db.placeJobs.findAvailable(
                PlaceParsingStatus.PENDING,
                PlaceParsingStatus.PROCESSING,
                now,
                now.minus(timeout),
                PageRequest.of(0, 1),
            ).map { it.postId },
        )
    }

    @Test
    fun `manual retry is atomic with audit and rejects duplicate requests`() {
        db.insertMediaJob()
        val job = db.jobs.claim(1, timeout).single()
        db.jobs.fail(job.id, job.attempt, "failed")
        val audit = RecordingAudit()
        val recovery = db.recovery(audit)
        val command = RetryParsingJobCommand(job.id, AdminActor("operator", "ops@example.com"), "복구", null)
        assertEquals("RETRY_PENDING", recovery.retry(command).recoveryStatus)
        assertFailsWith<ParsingRecoveryException> { recovery.retry(command) }
        assertEquals(1, audit.entries.size)
        val next = db.jobs.claim(1, timeout).single()
        assertEquals(job.attempt + 1, next.attempt)
        assertEquals(1, next.retryAttempt)
        assertFalse(db.jobs.complete(job.id, job.attempt))
        assertEquals("RETRY_PROCESSING", recovery.find(job.id)?.recoveryStatus)
        db.jobs.fail(next.id, next.attempt, "failed again")
        assertEquals("RETRY_FAILED", recovery.find(job.id)?.recoveryStatus)
        recovery.retry(command)
        val finalAttempt = db.jobs.claim(1, timeout).single()
        db.jobs.complete(finalAttempt.id, finalAttempt.attempt)
        assertEquals("RECOVERED", recovery.find(job.id)?.recoveryStatus)
        assertEquals("RECOVERED", recovery.list(1, "COMPLETED", null, 10).jobs.single().recoveryStatus)
    }

    @Test
    fun `audit failure rolls back retry reservation`() {
        db.insertMediaJob()
        val job = db.jobs.claim(1, timeout).single()
        db.jobs.fail(job.id, job.attempt, "failed")
        val recovery = db.recovery(RecordingAudit(fail = true))
        val command = RetryParsingJobCommand(job.id, AdminActor("operator", "ops@example.com"), "복구", null)
        assertFailsWith<IllegalStateException> { recovery.retry(command) }
        assertEquals("FAILED", recovery.list(1, "FAILED", null, 10).jobs.single().status)
        assertEquals(emptyList(), db.jobs.claim(1, timeout))
    }

    @Test
    fun `automatic retries are not reported as manual recovery`() {
        db.insertMediaJob()
        val first = db.jobs.claim(1, timeout).single()
        db.jobs.retry(first.id, first.attempt, ParsingMySqlFixture.NOW, "temporary failure")
        val second = db.jobs.claim(1, timeout).single()
        db.jobs.complete(second.id, second.attempt)
        val recovery = db.recovery(RecordingAudit())
        assertEquals("NONE", recovery.find(first.id)?.recoveryStatus)
        assertEquals(null, recovery.find(Long.MAX_VALUE))
    }

    @Test
    fun `post pagination keeps all stages together and retries failures atomically`() {
        db.insertMediaJob()
        val job = db.jobs.claim(1, timeout).single()
        db.jobs.fail(job.id, job.attempt, "failed")
        db.jdbc.update(
            """
            INSERT INTO post_content_parsing_jobs
                (post_id, status, attempt_count, retry_attempt_count, next_attempt_at, progress_percent)
            VALUES (1, 'COMPLETED', 1, 1, NOW(6), 45), (2, 'FAILED', 4, 4, NOW(6), 5)
            """.trimIndent(),
        )
        db.jdbc.update(
            """
            INSERT INTO place_parsing_jobs
                (post_id, status, attempt_count, retry_attempt_count, next_attempt_at, progress_percent)
            VALUES (1, 'FAILED', 4, 4, NOW(6), 74)
            """.trimIndent(),
        )
        val audit = RecordingAudit()
        val posts = db.posts(audit)
        val first = posts.list(null, "FAILED", null, 1)
        assertEquals(listOf(2L), first.posts.map { it.postId })
        assertEquals(true, first.hasNext)
        val second = posts.list(null, "FAILED", 2, 1)
        assertEquals(3, second.posts.single().jobs.size)
        assertEquals(false, second.hasNext)
        val command = RetryPostParsingCommand(1, AdminActor("operator", "ops@example.com"), "복구")
        assertFailsWith<IllegalStateException> { db.posts(RecordingAudit(fail = true)).retry(command) }
        assertEquals(2, posts.find(1)!!.jobs.count { it.status == "FAILED" })
        val result = posts.retry(command)
        assertEquals(1, result.jobs.count { it.status == "COMPLETED" })
        assertEquals(2, result.jobs.count { it.recoveryStatus == "RETRY_PENDING" })
        assertEquals(4, result.jobs.single { it.type == "PLACE_PARSING" }.attempts)
        assertEquals(ParsingMySqlFixture.NOW, result.jobs.single { it.type == "POST_MEDIA" }.lastFailedAt)
        assertEquals(1, audit.entries.size)
        assertFailsWith<ParsingRecoveryException> { posts.retry(command) }
        assertEquals(listOf(2L), posts.list(null, "FAILED", null, 20).posts.map { it.postId })
    }

    @Test
    fun `failure time survives manual retry and unrelated metadata updates`() {
        db.insertMediaJob()
        val job = db.jobs.claim(1, timeout).single()
        assertFalse(db.jobs.fail(job.id, job.attempt + 1, "stale"))
        val recovery = db.recovery(RecordingAudit())
        assertEquals(null, recovery.find(job.id)?.lastFailedAt)
        db.jobs.fail(job.id, job.attempt, "failed")
        db.jdbc.update("UPDATE parsing_follow_up_jobs SET updated_at = '2026-09-14 00:00:00'")
        assertEquals(ParsingMySqlFixture.NOW, recovery.find(job.id)?.lastFailedAt)
        recovery.retry(RetryParsingJobCommand(job.id, AdminActor("operator", "ops@example.com"), "복구", null))
        val retry = db.jobs.claim(1, timeout).single()
        db.jobs.complete(retry.id, retry.attempt)
        assertEquals(ParsingMySqlFixture.NOW, recovery.find(job.id)?.lastFailedAt)
    }

    private class RecordingAudit(private val fail: Boolean = false) : AdminAuditLogPort {
        val entries = mutableListOf<AdminAuditLogPort.Entry>()
        override fun listAuditLogs(targetType: String?, targetId: String?, offset: Int, limit: Int) = error("not used")
        override fun append(entry: AdminAuditLogPort.Entry) {
            check(!fail) { "audit unavailable" }
            entries += entry
        }
    }

    private companion object {
        const val WAIT_SECONDS = 20L
    }
}
