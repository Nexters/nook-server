package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.infrastructure.persistence.post.PostMediaEntity
import org.every.nook.api.infrastructure.persistence.post.PostMediaJpaRepository
import org.every.nook.api.infrastructure.storage.MediaStorageProperties
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.support.StaticListableBeanFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class PostMediaOcrReadinessAdapterTest {
    private val mediaRepository = mock(PostMediaJpaRepository::class.java)

    @Test
    fun `image urls are ready for OCR when media storage is disabled`() {
        `when`(
            mediaRepository.findFirst20ByPostIdAndMediaTypeOrderBySequenceAsc(11, PostMedia.MediaType.IMAGE),
        ).thenReturn(listOf(PostMediaEntity(11, PostMedia.MediaType.IMAGE, "https://instagram-cdn.test/1.jpg", 0)))

        assertEquals(true, adapter().areImageUrlsReadyForOcr(11))
    }

    @Test
    fun `image urls are not ready for OCR until all images are stored through media storage`() {
        `when`(
            mediaRepository.findFirst20ByPostIdAndMediaTypeOrderBySequenceAsc(11, PostMedia.MediaType.IMAGE),
        ).thenReturn(
            listOf(
                PostMediaEntity(11, PostMedia.MediaType.IMAGE, "https://media.nook.example/post-media/1.jpg", 0),
                PostMediaEntity(11, PostMedia.MediaType.IMAGE, "https://instagram-cdn.test/2.jpg", 1),
            ),
        )

        assertEquals(false, adapter(mediaStorageProperties()).areImageUrlsReadyForOcr(11))
    }

    @Test
    fun `image urls are ready for OCR when all images are stored through media storage`() {
        `when`(
            mediaRepository.findFirst20ByPostIdAndMediaTypeOrderBySequenceAsc(11, PostMedia.MediaType.IMAGE),
        ).thenReturn(
            listOf(
                PostMediaEntity(11, PostMedia.MediaType.IMAGE, "https://media.nook.example/post-media/1.jpg", 0),
                PostMediaEntity(11, PostMedia.MediaType.IMAGE, "https://media.nook.example/post-media/2.jpg", 1),
            ),
        )

        assertEquals(true, adapter(mediaStorageProperties()).areImageUrlsReadyForOcr(11))
    }

    private fun adapter(mediaStorageProperties: MediaStorageProperties? = null): PostMediaOcrReadinessAdapter {
        val beanFactory = StaticListableBeanFactory()
        if (mediaStorageProperties != null) {
            beanFactory.addBean("mediaStorageProperties", mediaStorageProperties)
        }
        return PostMediaOcrReadinessAdapter(
            mediaRepository = mediaRepository,
            mediaStoragePropertiesProvider = beanFactory.getBeanProvider(MediaStorageProperties::class.java),
        )
    }

    private fun mediaStorageProperties(): MediaStorageProperties = MediaStorageProperties(
        enabled = true,
        bucket = "nook-media",
        cloudFrontBaseUrl = "https://media.nook.example",
    )
}
