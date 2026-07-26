package org.every.nook.api.infrastructure.instagram

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.every.nook.api.domain.post.PostMedia
import kotlin.test.Test
import kotlin.test.assertEquals

class BrightDataInstagramMapperTest {
    private val objectMapper = jacksonObjectMapper()
    private val mapper = BrightDataInstagramMapper()

    @Test
    fun `carousel post preserves media order and location names`() {
        val record = objectMapper.readValue<BrightDataInstagramRecord>(
            """
            {
              "url": "https://www.instagram.com/p/Post123/",
              "user_posted": "nook",
              "description": "서울의 작은 카페",
              "hashtags": ["#서울", "#카페"],
              "content_type": "Carousel",
              "thumbnail": "https://cdn.example/thumbnail.jpg",
              "date_posted": "2026-07-23T03:00:00.000Z",
              "location": ["Nook Cafe", null],
              "location_details": {
                "pk": "place-1",
                "name": "Nook Cafe",
                "lat": "37.1234",
                "lng": 127.5678,
                "profile_pic_url": "https://cdn.example/place.jpg"
              },
              "post_content": [
                {"index": 1, "type": "Video", "url": "https://cdn.example/video.mp4"},
                {"index": 0, "type": "Photo", "url": "https://cdn.example/photo.jpg", "alt_text": "cafe"}
              ]
            }
            """.trimIndent(),
        )

        val result = mapper.map(InstagramContentUrl.parse(record.url!!), record)

        assertEquals(listOf(PostMedia.MediaType.IMAGE, PostMedia.MediaType.VIDEO), result.post.media.map { it.type })
        assertEquals(listOf(0, 1), result.post.media.map { it.sequence })
        assertEquals(listOf("Nook Cafe"), result.sourceLocationNames)
    }

    @Test
    fun `reel is mapped without location`() {
        val record = objectMapper.readValue<BrightDataInstagramRecord>(
            """
            {
              "url": "https://www.instagram.com/reel/Reel123/",
              "description": null,
              "hashtags": null,
              "thumbnail": "https://cdn.example/reel.jpg",
              "video_url": "https://cdn.example/reel.mp4",
              "location": null,
              "location_details": null
            }
            """.trimIndent(),
        )

        val result = mapper.map(InstagramContentUrl.parse(record.url!!), record)

        assertEquals(PostMedia.MediaType.VIDEO, result.post.media.single().type)
        assertEquals("https://cdn.example/reel.mp4", result.post.media.single().url)
        assertEquals(emptyList(), result.sourceLocationNames)
    }
}
