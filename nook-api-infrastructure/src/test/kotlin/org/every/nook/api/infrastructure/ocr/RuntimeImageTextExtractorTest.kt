package org.every.nook.api.infrastructure.ocr

import org.every.nook.api.application.config.RuntimeConfigurationReader
import org.every.nook.api.application.place.ImageTextExtractor
import org.every.nook.api.application.place.ImageTranscript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RuntimeImageTextExtractorTest {
    private val request = ImageTextExtractor.Request(
        listOf(ImageTextExtractor.ImageInput(3, "https://example.com/image.jpg")),
    )

    @Test
    fun `falls back in configured order on error and empty transcript`() {
        val calls = mutableListOf<OcrProviderType>()
        val extractor = extractor(
            "COREPIN,CLOVA,OPENAI",
            mapOf(
                OcrProviderType.COREPIN to provider(calls, OcrProviderType.COREPIN) { error("temporary") },
                OcrProviderType.CLOVA to provider(calls, OcrProviderType.CLOVA) {
                    listOf(ImageTranscript(3, emptyList()))
                },
                OcrProviderType.OPENAI to provider(calls, OcrProviderType.OPENAI) {
                    listOf(ImageTranscript(3, listOf("가람커피", "서울 성동구")))
                },
            ),
        )

        val result = extractor.extract(request)

        assertEquals(listOf(OcrProviderType.COREPIN, OcrProviderType.CLOVA, OcrProviderType.OPENAI), calls)
        assertEquals(listOf("가람커피", "서울 성동구"), result.single().texts)
    }

    @Test
    fun `stops after first provider with text`() {
        val calls = mutableListOf<OcrProviderType>()
        val extractor = extractor(
            "CLOVA,COREPIN,OPENAI",
            mapOf(
                OcrProviderType.CLOVA to provider(calls, OcrProviderType.CLOVA) {
                    listOf(ImageTranscript(3, listOf("누리상점")))
                },
                OcrProviderType.COREPIN to provider(calls, OcrProviderType.COREPIN) { error("must not run") },
            ),
        )

        assertEquals(listOf("누리상점"), extractor.extract(request).single().texts)
        assertEquals(listOf(OcrProviderType.CLOVA), calls)
    }

    @Test
    fun `uses default chain for invalid configuration`() {
        val calls = mutableListOf<OcrProviderType>()
        val extractor = extractor(
            "unknown",
            mapOf(
                OcrProviderType.OPENAI to provider(calls, OcrProviderType.OPENAI) {
                    listOf(ImageTranscript(3, listOf("들꽃카페")))
                },
            ),
        )

        extractor.extract(request)

        assertEquals(listOf(OcrProviderType.OPENAI), calls)
    }

    @Test
    fun `fails after every provider fails`() {
        val extractor = extractor(
            "COREPIN,CLOVA,OPENAI",
            OcrProviderType.entries.associateWith { ImageTextExtractor { error("failed") } },
        )

        assertFailsWith<IllegalStateException> { extractor.extract(request) }
    }

    private fun extractor(
        configuration: String,
        providers: Map<OcrProviderType, ImageTextExtractor>,
    ): RuntimeImageTextExtractor = RuntimeImageTextExtractor(RuntimeConfigurationReader { configuration }, providers)

    private fun provider(
        calls: MutableList<OcrProviderType>,
        type: OcrProviderType,
        response: () -> List<ImageTranscript>,
    ) = ImageTextExtractor {
        calls += type
        response()
    }
}
