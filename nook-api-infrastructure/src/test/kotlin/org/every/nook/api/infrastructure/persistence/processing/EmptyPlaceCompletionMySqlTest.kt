package org.every.nook.api.infrastructure.persistence.processing

import org.every.nook.api.application.place.PlaceParsingDiagnostics
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingPersistenceAdapter
import org.every.nook.api.infrastructure.persistence.post.PostEntity
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmptyPlaceCompletionMySqlTest {
    @Test
    fun `empty place completion commits diagnostics without tag or thumbnail jobs`() {
        ParsingMySqlFixture().use { db ->
            val posts = mock(PostJpaRepository::class.java)
            val post = PostEntity("INSTAGRAM", "one", "https://instagram.com/p/one/")
            `when`(posts.findById(1)).thenReturn(Optional.of(post))
            val adapter = PlaceParsingPersistenceAdapter(
                jobRepository = db.placeJobs,
                postRepository = posts,
                hashtagRepository = mock(),
                mediaRepository = mock(),
                placeRepository = mock(),
                placeIdentityResolver = mock(),
                postPlaceRepository = mock(),
                userSavedPostLockRepository = mock(),
                userSavedPostPlaceRepository = mock(),
                userPlaceBookmarkRepository = mock(),
                sharedBookmarkSyncRepository = mock(),
                postPlaceTagRepository = mock(),
                postPlaceReviewRepository = mock(),
                followUpJobPort = db.jobs,
                objectMapper = jacksonObjectMapper(),
            )
            db.jdbc.update(
                """
                INSERT INTO place_parsing_jobs
                    (post_id,status,attempt_count,retry_attempt_count,next_attempt_at,progress_percent)
                VALUES (1,'PROCESSING',1,1,NOW(6),50)
                """.trimIndent(),
            )
            val diagnostics =
                PlaceParsingDiagnostics(PlaceParsingDiagnostics.Outcome.COMPLETED, null, 0, 0, emptyList())
            db.transaction { assertTrue(adapter.complete(1, 1, "장소 없는 게시물", emptyList(), diagnostics)) }
            val stored = db.placeJobs.findByPostId(1)!!
            assertEquals(PlaceParsingStatus.COMPLETED, stored.status)
            assertEquals(PlaceParsingDiagnostics.Outcome.COMPLETED, stored.parsingOutcome)
            assertEquals(0, stored.resolvedPlaceCount)
            assertEquals(100, stored.progressPercent)
            assertEquals(null, stored.failureReason)
            assertEquals(1, stored.attemptCount)
            assertEquals(0, db.jdbc.queryForObject("SELECT COUNT(*) FROM parsing_follow_up_jobs", Int::class.java))
        }
    }
}
