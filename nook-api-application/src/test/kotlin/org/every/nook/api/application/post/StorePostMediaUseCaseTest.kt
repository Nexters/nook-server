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
            updateMediaUrl = UpdatePostMediaUrlPort {
                    postId,
                    sequence,
                    sourceUrl,
                    storedUrl,
                    sourceThumbnailUrl,
                    storedThumbnailUrl,
                ->
                calls +=
                    "update:$postId:$sequence:$sourceUrl:$storedUrl:$sourceThumbnailUrl:$storedThumbnailUrl"
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
                "update:11:0:https://source.example.com/image.jpg:https://cdn.example.com/image.jpg:null:null",
            ),
            calls,
        )
    }

    @Test
    fun `stores a video thumbnail as an image before replacing both persisted urls`() {
        val calls = mutableListOf<String>()
        val useCase = StorePostMediaUseCase(
            mediaStorage = PostMediaStoragePort { media ->
                calls += "store:${media.type}:${media.url}"
                media.copy(url = "https://stored.example.com/${media.type.name.lowercase()}")
            },
            updateMediaUrl = UpdatePostMediaUrlPort {
                    _,
                    _,
                    _,
                    storedUrl,
                    _,
                    storedThumbnailUrl,
                ->
                calls += "update:$storedUrl:$storedThumbnailUrl"
            },
        )

        useCase(
            11,
            StorePostMediaUseCase.Command(
                mediaType = PostMedia.MediaType.VIDEO.name,
                sourceUrl = "https://source.example.com/video.mp4",
                sequence = 0,
                sourceThumbnailUrl = "https://source.example.com/poster.jpg",
            ),
        )

        assertEquals(
            listOf(
                "store:VIDEO:https://source.example.com/video.mp4",
                "store:IMAGE:https://source.example.com/poster.jpg",
                "update:https://stored.example.com/video:https://stored.example.com/image",
            ),
            calls,
        )
    }
}
