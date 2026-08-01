package org.every.nook.api.application.post

import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.application.post.port.UpdatePostMediaUrlPort
import org.every.nook.api.domain.post.PostMedia
import kotlin.test.Test
import kotlin.test.assertEquals

class StorePostMediaUseCaseTest {
    @Test
    fun `stores media and replaces only its persisted source url`() {
        val calls = mutableListOf<String>()
        val useCase = StorePostMediaUseCase(
            mediaStorage = PostMediaStoragePort { media ->
                calls += "store:${media.url}"
                media.copy(url = "https://cdn.example.com/image.jpg")
            },
            updateMediaUrl = UpdatePostMediaUrlPort { postId, sequence, sourceUrl, storedUrl ->
                calls += "update:$postId:$sequence:$sourceUrl:$storedUrl"
            },
        )

        useCase(
            11,
            StorePostMediaUseCase.Command(
                mediaType = PostMedia.MediaType.IMAGE.name,
                sourceUrl = "https://source.example.com/image.jpg",
                sequence = 0,
            ),
        )

        assertEquals(
            listOf(
                "store:https://source.example.com/image.jpg",
                "update:11:0:https://source.example.com/image.jpg:https://cdn.example.com/image.jpg",
            ),
            calls,
        )
    }
}
