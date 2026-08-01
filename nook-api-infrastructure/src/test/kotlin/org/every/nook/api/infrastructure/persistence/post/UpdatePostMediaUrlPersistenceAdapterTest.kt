package org.every.nook.api.infrastructure.persistence.post

import org.every.nook.api.domain.post.PostMedia
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdatePostMediaUrlPersistenceAdapterTest {
    private val repository = mock(PostMediaJpaRepository::class.java)
    private val adapter = UpdatePostMediaUrlPersistenceAdapter(repository)

    @Test
    fun `replaces the url when the persisted media still points to its source`() {
        val media = PostMediaEntity(
            postId = 11,
            mediaType = PostMedia.MediaType.IMAGE,
            mediaUrl = "https://source.example.com/image.jpg",
            sequence = 0,
        )
        `when`(repository.findByPostIdAndSequence(11, 0)).thenReturn(media)

        adapter.update(
            postId = 11,
            sequence = 0,
            sourceUrl = "https://source.example.com/image.jpg",
            storedUrl = "https://cdn.example.com/image.jpg",
        )

        assertEquals("https://cdn.example.com/image.jpg", media.mediaUrl)
    }

    @Test
    fun `keeps a newer url when a duplicate storage event finishes late`() {
        val media = PostMediaEntity(
            postId = 11,
            mediaType = PostMedia.MediaType.IMAGE,
            mediaUrl = "https://cdn.example.com/newer.jpg",
            sequence = 0,
        )
        `when`(repository.findByPostIdAndSequence(11, 0)).thenReturn(media)

        adapter.update(
            postId = 11,
            sequence = 0,
            sourceUrl = "https://source.example.com/image.jpg",
            storedUrl = "https://cdn.example.com/older.jpg",
        )

        assertEquals("https://cdn.example.com/newer.jpg", media.mediaUrl)
    }
}
