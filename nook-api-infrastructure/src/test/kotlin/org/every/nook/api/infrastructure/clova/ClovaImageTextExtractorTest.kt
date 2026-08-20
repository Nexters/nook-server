package org.every.nook.api.infrastructure.clova

import org.every.nook.api.application.place.ImageTextExtractor
import org.every.nook.api.infrastructure.vision.VisionImageDownloader
import org.hamcrest.Matchers.containsString
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class ClovaImageTextExtractorTest {
    @Test
    fun `sends base64 image and extracts inferred fields`() {
        val apiBuilder = RestClient.builder()
        val apiServer = MockRestServiceServer.bindTo(apiBuilder).build()
        val imageBuilder = RestClient.builder()
        val imageServer = MockRestServiceServer.bindTo(imageBuilder).build()
        imageServer.expect(requestTo("https://cdn.test/image.jpg"))
            .andRespond(withSuccess(byteArrayOf(1, 2, 3), MediaType.IMAGE_JPEG))
        apiServer.expect(requestTo("https://clova.test/general"))
            .andExpect(header("X-OCR-SECRET", "test-secret"))
            .andExpect(content().string(containsString("\"version\":\"V2\"")))
            .andExpect(content().string(containsString("\"data\":\"AQID\"")))
            .andRespond(
                withSuccess(
                    """
                    {"images":[{"inferResult":"SUCCESS","fields":[
                      {"inferText":"마더오프라인"},{"inferText":"서울 성동구 연무장길 1"}
                    ]}]}
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )
        val properties = ClovaOcrProperties(
            invokeUrl = "https://clova.test/general",
            secretKey = "test-secret",
        )
        val extractor = ClovaImageTextExtractor(
            apiBuilder.build(),
            jacksonObjectMapper(),
            properties,
            VisionImageDownloader(imageBuilder.build(), properties.maxImageBytes),
            Clock.fixed(Instant.ofEpochMilli(1234), ZoneOffset.UTC),
        )

        val result = extractor.extract(
            ImageTextExtractor.Request(listOf(ImageTextExtractor.ImageInput(4, "https://cdn.test/image.jpg"))),
        )

        assertEquals(listOf("마더오프라인", "서울 성동구 연무장길 1"), result.single().texts)
        apiServer.verify()
        imageServer.verify()
    }
}
