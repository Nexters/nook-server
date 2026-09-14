package org.every.nook.api.infrastructure.persistence.processing

import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.admin.ChangePostDisposition
import org.every.nook.api.application.admin.ParsingRecoveryException
import org.every.nook.api.application.admin.PostProcessingQuery
import org.every.nook.api.application.admin.RetryParsingJobCommand
import org.every.nook.api.application.admin.RetryPostParsingCommand
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostProcessingMySqlTest {
    private lateinit var db: ParsingMySqlFixture
    private val actor = AdminActor("operator", "ops@example.com")
    private val audit = object : AdminAuditLogPort {
        override fun listAuditLogs(targetType: String?, targetId: String?, offset: Int, limit: Int) = error("unused")
        override fun append(entry: AdminAuditLogPort.Entry) = Unit
    }

    @BeforeAll
    fun start() {
        db = ParsingMySqlFixture()
    }

    @AfterAll
    fun stop() {
        if (::db.isInitialized) db.close()
    }

    @BeforeTest
    fun reset() {
        db.jdbc.update("DELETE FROM parsing_follow_up_jobs")
        db.jdbc.update("DELETE FROM post_content_parsing_jobs")
        db.jdbc.update("DELETE FROM place_parsing_jobs")
        db.jdbc.update("UPDATE posts SET processing_disposition='OPEN', processing_disposition_reason=NULL")
        db.insertMediaJob()
        db.jdbc.update(
            """
            UPDATE parsing_follow_up_jobs SET status='FAILED',attempt_count=4,retry_attempt_count=4,
            last_failed_at='2026-09-10 00:00:00',failure_reason='download HTTP 403: access denied'
            """.trimIndent(),
        )
    }

    @Test
    fun `failure date filter uses real failure time and separates administrator retries`() {
        val port = db.processing(audit)
        assertEquals(1, port.list(PostProcessingQuery()).posts.size)
        assertEquals(0, port.list(PostProcessingQuery(failedSince = Instant.parse("2026-09-11T00:00:00Z"))).posts.size)
        db.jdbc.update("UPDATE parsing_follow_up_jobs SET attempt_count=8,retry_attempt_count=4")
        assertEquals(0, port.list(PostProcessingQuery()).posts.size)
        val retry = port.list(PostProcessingQuery(category = "RETRY_FAILED")).posts.single()
        assertEquals("OPEN", retry.disposition)
        assertEquals("403", retry.jobs.single().failure?.code)
        assertEquals(0, port.list(PostProcessingQuery(category = "RETRY_FAILED", page = 2)).posts.size)
    }

    @Test
    fun `manual disposition preserves failures blocks retry and can be reopened without running work`() {
        val port = db.processing(audit)
        val before = db.posts(audit).find(1)!!.jobs.single()
        val changed = port.change(ChangePostDisposition(1, "UNPROCESSABLE", "원본 복구 불가", actor))
        assertEquals("UNPROCESSABLE", changed.disposition)
        assertEquals(before.attempts, changed.jobs.single().attempts)
        assertEquals(before.lastFailedAt, changed.jobs.single().lastFailedAt)
        assertEquals("FAILED", changed.jobs.single().status)
        assertEquals(0, port.list(PostProcessingQuery()).posts.size)
        assertEquals(1, port.list(PostProcessingQuery(category = "UNPROCESSABLE")).posts.size)
        assertFailsWith<ParsingRecoveryException> { db.posts(audit).retry(RetryPostParsingCommand(1, actor, "retry")) }
        assertFailsWith<ParsingRecoveryException> {
            db.recovery(audit).retry(RetryParsingJobCommand(before.id, actor, "retry", null))
        }
        val reopened = port.change(ChangePostDisposition(1, "OPEN", "원인 조치", actor))
        assertEquals("FAILED", reopened.jobs.single().status)
        assertEquals(1, port.list(PostProcessingQuery()).posts.size)
        assertEquals("PENDING", db.posts(audit).retry(RetryPostParsingCommand(1, actor, "retry")).jobs.single().status)
    }

    @Test
    fun `audit failure rolls back disposition and active work cannot be marked impossible`() {
        assertFailsWith<IllegalStateException> {
            db.processing(object : AdminAuditLogPort {
                override fun listAuditLogs(targetType: String?, targetId: String?, offset: Int, limit: Int) =
                    error("unused")
                override fun append(entry: AdminAuditLogPort.Entry) {
                    error("audit unavailable")
                }
            })
                .change(ChangePostDisposition(1, "UNPROCESSABLE", "조사", actor))
        }
        assertEquals("OPEN", db.posts(audit).find(1)!!.disposition)
        db.jdbc.update("UPDATE parsing_follow_up_jobs SET status='PROCESSING'")
        assertFailsWith<ParsingRecoveryException> {
            db.processing(audit).change(ChangePostDisposition(1, "UNPROCESSABLE", "조사", actor))
        }
    }

    @Test
    fun `two operators cannot apply the same disposition twice`() {
        val barrier = CyclicBarrier(2)
        Executors.newFixedThreadPool(2).use { pool ->
            val results = (1..2).map {
                pool.submit(
                    Callable {
                        barrier.await(10, TimeUnit.SECONDS)
                        runCatching {
                            db.processing(
                                audit,
                            ).change(ChangePostDisposition(1, "UNPROCESSABLE", "조사", actor))
                        }
                    },
                )
            }.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it.isSuccess })
            assertTrue(results.single { it.isFailure }.exceptionOrNull() is ParsingRecoveryException)
        }
    }
}
