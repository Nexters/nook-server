package org.every.nook.api.domain.place

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlaceParsingJobTest {
    @Test
    fun `creates a pending job for a post`() {
        val job = PlaceParsingJob(postId = 1, status = PlaceParsingStatus.PENDING)

        assertEquals(1, job.postId)
        assertEquals(PlaceParsingStatus.PENDING, job.status)
    }

    @Test
    fun `rejects a non positive post id`() {
        assertFailsWith<IllegalArgumentException> {
            PlaceParsingJob(postId = 0, status = PlaceParsingStatus.PENDING)
        }
    }
}
