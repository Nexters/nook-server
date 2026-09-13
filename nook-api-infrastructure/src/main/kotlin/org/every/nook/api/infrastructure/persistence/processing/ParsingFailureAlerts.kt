package org.every.nook.api.infrastructure.persistence.processing

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant

/** Emits one terminal event only after the fenced failure transition commits. */
@Component
class ParsingFailureAlerts {
    fun afterCommit(postId: Long, type: String, jobId: Long, attempt: Int, stage: String, failedAt: Instant) {
        check(TransactionSynchronizationManager.isSynchronizationActive())
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                LoggerFactory.getLogger(ParsingFailureAlerts::class.java).atError()
                    .addKeyValue("event_type", "post.parsing.failed")
                    .addKeyValue("post_id", postId)
                    .addKeyValue("job_type", type)
                    .addKeyValue("job_id", jobId)
                    .addKeyValue("attempt", attempt)
                    .addKeyValue("failure_stage", stage)
                    .addKeyValue("failed_at", failedAt.toString())
                    .log("게시물 파싱 최종 실패: postId={}, stage={}, jobId={}, attempt={}", postId, stage, jobId, attempt)
            }
        })
    }
}
