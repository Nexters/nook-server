package org.every.nook.api.infrastructure.corepin

import org.every.nook.api.application.place.ImageTextExtractor
import org.every.nook.api.infrastructure.vision.VisionImageDownloader
import org.hamcrest.Matchers.containsString
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals

class CorepinImageTextExtractorTest {
    @Test
    fun `uploads one image and extracts text lines`() {
        val apiBuilder = RestClient.builder().baseUrl("https://corepin.test")
        val apiServer = MockRestServiceServer.bindTo(apiBuilder).build()
        val imageBuilder = RestClient.builder()
        val imageServer = MockRestServiceServer.bindTo(imageBuilder).build()
        imageServer.expect(requestTo("https://cdn.test/image.jpg"))
            .andRespond(withSuccess(byteArrayOf(1, 2, 3), MediaType.IMAGE_JPEG))
        apiServer.expect(requestTo("https://corepin.test/v1/ocr"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
            .andExpect(content().string(containsString("name=\"format\"")))
            .andExpect(content().string(containsString("name=\"image\"")))
            .andRespond(
                withSuccess(
                    """{"text":"에이치커피로스터스\n서울 성동구 성수이로 1"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
        val properties = CorepinOcrProperties(baseUrl = "https://corepin.test", apiKey = "test-key")
        val extractor = CorepinImageTextExtractor(
            apiBuilder.build(),
            jacksonObjectMapper(),
            properties,
            VisionImageDownloader(imageBuilder.build(), properties.maxImageBytes),
        )

        val result = extractor.extract(
            ImageTextExtractor.Request(listOf(ImageTextExtractor.ImageInput(2, "https://cdn.test/image.jpg"))),
        )

        assertEquals(2, result.single().imageIndex)
        assertEquals(listOf("에이치커피로스터스", "서울 성동구 성수이로 1"), result.single().texts)
        apiServer.verify()
        imageServer.verify()
    }
}
