package org.every.nook.api.infrastructure.vision

import org.every.nook.api.application.place.ImageTextExtractor
import org.every.nook.api.application.place.ImageTranscript
import org.hamcrest.Matchers.containsString
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GoogleCloudVisionImageTextExtractorTest {
    @Test
    fun `extracts line transcripts with Cloud Vision document text detection`() {
        val fixture = extractorFixture()
        fixture.server.expect(requestTo("https://vision.test/v1/images:annotate?key=test-key"))
            .andExpect(content().string(containsString("DOCUMENT_TEXT_DETECTION")))
            .andExpect(content().string(containsString(ENCODED_IMAGE)))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("imageUri"))))
            .andExpect(content().string(containsString("languageHints")))
            .andRespond(
                withSuccess(
                    """
                    {
                      "responses": [
                        {
                          "fullTextAnnotation": {
                            "text": "빈브라더스 커피하우스 서울\n서울 마포구 상수동 354-12\n"
                          }
                        },
                        {
                          "fullTextAnnotation": {
                            "text": "누뗀\n서울 서초구 신원동 489-7"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val transcripts = fixture.extractor.extract(
            ImageTextExtractor.Request(
                listOf(
                    ImageTextExtractor.ImageInput(2, "https://cdn.example.com/1.jpg"),
                    ImageTextExtractor.ImageInput(3, "https://cdn.example.com/2.jpg"),
                ),
            ),
        )

        assertEquals(listOf(2, 3), transcripts.map(ImageTranscript::imageIndex))
        assertEquals(
            listOf("빈브라더스 커피하우스 서울", "서울 마포구 상수동 354-12"),
            transcripts.first().texts,
        )
        assertEquals(listOf("누뗀", "서울 서초구 신원동 489-7"), transcripts.last().texts)
        fixture.server.verify()
    }

    @Test
    fun `falls back to text annotations when full text annotation is absent`() {
        val fixture = extractorFixture(imageCount = 1)
        fixture.server.expect(requestTo("https://vision.test/v1/images:annotate?key=test-key"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "responses": [
                        {
                          "textAnnotations": [
                            {"description": "모로코코 카페\n서울 성동구"}
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val transcripts = fixture.extractor.extract(
            ImageTextExtractor.Request(listOf(ImageTextExtractor.ImageInput(1, "https://cdn.example.com/1.jpg"))),
        )

        assertEquals(listOf("모로코코 카페", "서울 성동구"), transcripts.single().texts)
        fixture.server.verify()
    }

    @Test
    fun `fails when Cloud Vision returns an annotation error`() {
        val fixture = extractorFixture(imageCount = 1)
        fixture.server.expect(requestTo("https://vision.test/v1/images:annotate?key=test-key"))
            .andRespond(
                withSuccess(
                    """{"responses":[{"error":{"message":"API key not valid"}}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertFailsWith<IllegalStateException> {
            fixture.extractor.extract(
                ImageTextExtractor.Request(listOf(ImageTextExtractor.ImageInput(1, "https://cdn.example.com/1.jpg"))),
            )
        }
        fixture.server.verify()
    }

    @Test
    fun `fails when the image cannot be downloaded`() {
        val builder = RestClient.builder().baseUrl("https://vision.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val imageBuilder = RestClient.builder()
        val imageServer = MockRestServiceServer.bindTo(imageBuilder).build()
        imageServer.expect(requestTo("https://cdn.example.com/1.jpg"))
            .andRespond(MockRestResponseCreators.withServerError())
        val properties = GoogleCloudVisionProperties(baseUrl = "https://vision.test", apiKey = "test-key")
        val extractor = GoogleCloudVisionImageTextExtractor(
            restClient = builder.build(),
            objectMapper = jacksonObjectMapper(),
            properties = properties,
            imageDownloader = VisionImageDownloader(imageBuilder.build(), properties.maxImageBytes),
        )

        val failure = assertFailsWith<IllegalStateException> {
            extractor.extract(
                ImageTextExtractor.Request(listOf(ImageTextExtractor.ImageInput(1, "https://cdn.example.com/1.jpg"))),
            )
        }

        assertEquals(true, failure.message?.contains("Failed to download image"))
        // Vision 은 아예 호출되지 않는다.
        server.verify()
    }

    @Test
    fun `fails when the downloaded image exceeds the size limit`() {
        val builder = RestClient.builder().baseUrl("https://vision.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val imageBuilder = RestClient.builder()
        val imageServer = MockRestServiceServer.bindTo(imageBuilder).build()
        imageServer.expect(requestTo("https://cdn.example.com/1.jpg"))
            .andRespond(withSuccess(ByteArray(16), MediaType.IMAGE_JPEG))
        val properties = GoogleCloudVisionProperties(baseUrl = "https://vision.test", apiKey = "test-key")
        val extractor = GoogleCloudVisionImageTextExtractor(
            restClient = builder.build(),
            objectMapper = jacksonObjectMapper(),
            properties = properties,
            imageDownloader = VisionImageDownloader(imageBuilder.build(), maxImageBytes = 8),
        )

        val failure = assertFailsWith<IllegalStateException> {
            extractor.extract(
                ImageTextExtractor.Request(listOf(ImageTextExtractor.ImageInput(1, "https://cdn.example.com/1.jpg"))),
            )
        }

        assertEquals(true, failure.message?.contains("too large"))
        server.verify()
    }

    private fun extractorFixture(imageCount: Int = 2): Fixture {
        val builder = RestClient.builder().baseUrl("https://vision.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val imageBuilder = RestClient.builder()
        val imageServer = MockRestServiceServer.bindTo(imageBuilder).build()
        repeat(imageCount) { index ->
            imageServer.expect(requestTo("https://cdn.example.com/${index + 1}.jpg"))
                .andRespond(withSuccess(IMAGE_BYTES, MediaType.IMAGE_JPEG))
        }
        val properties = GoogleCloudVisionProperties(baseUrl = "https://vision.test", apiKey = "test-key")
        val extractor = GoogleCloudVisionImageTextExtractor(
            restClient = builder.build(),
            objectMapper = jacksonObjectMapper(),
            properties = properties,
            imageDownloader = VisionImageDownloader(imageBuilder.build(), properties.maxImageBytes),
        )
        return Fixture(extractor, server, imageServer)
    }

    private companion object {
        val IMAGE_BYTES = byteArrayOf(1, 2, 3, 4)
        val ENCODED_IMAGE: String = java.util.Base64.getEncoder().encodeToString(IMAGE_BYTES)
    }

    private data class Fixture(
        val extractor: GoogleCloudVisionImageTextExtractor,
        val server: MockRestServiceServer,
        val imageServer: MockRestServiceServer,
    )
}
