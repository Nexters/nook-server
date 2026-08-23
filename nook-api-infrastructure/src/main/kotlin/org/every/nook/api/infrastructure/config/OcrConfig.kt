package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.billing.NoOpExternalApiUsageMeter
import org.every.nook.api.application.config.RuntimeConfigurationReader
import org.every.nook.api.application.place.ImageTextExtractor
import org.every.nook.api.infrastructure.billing.ExternalApiCallMeter
import org.every.nook.api.infrastructure.clova.ClovaImageTextExtractor
import org.every.nook.api.infrastructure.clova.ClovaOcrProperties
import org.every.nook.api.infrastructure.corepin.CorepinImageTextExtractor
import org.every.nook.api.infrastructure.corepin.CorepinOcrProperties
import org.every.nook.api.infrastructure.ocr.OcrProviderType
import org.every.nook.api.infrastructure.ocr.RuntimeImageTextExtractor
import org.every.nook.api.infrastructure.openai.OpenAiImageTextExtractor
import org.every.nook.api.infrastructure.vision.VisionImageDownloader
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper

@Configuration
@EnableConfigurationProperties(CorepinOcrProperties::class, ClovaOcrProperties::class)
class OcrConfig {
    @Bean("ocrImageRestClient")
    fun ocrImageRestClient(): RestClient = RestClient.create()

    @Bean("corepinOcrRestClient")
    fun corepinOcrRestClient(properties: CorepinOcrProperties): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.connectTimeout)
            setReadTimeout(properties.readTimeout)
        }
        return RestClient.builder().baseUrl(properties.baseUrl).requestFactory(requestFactory).build()
    }

    @Bean("clovaOcrRestClient")
    fun clovaOcrRestClient(properties: ClovaOcrProperties): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.connectTimeout)
            setReadTimeout(properties.readTimeout)
        }
        return RestClient.builder().requestFactory(requestFactory).build()
    }

    @Bean("corepinImageTextExtractor")
    fun corepinImageTextExtractor(
        @Qualifier("corepinOcrRestClient") restClient: RestClient,
        @Qualifier("ocrImageRestClient") imageRestClient: RestClient,
        properties: CorepinOcrProperties,
        callMeter: ObjectProvider<ExternalApiCallMeter>,
    ): CorepinImageTextExtractor = CorepinImageTextExtractor(
        restClient = restClient,
        objectMapper = jacksonObjectMapper(),
        properties = properties,
        imageDownloader = VisionImageDownloader(imageRestClient, properties.maxImageBytes),
        callMeter = callMeter.ifAvailable ?: ExternalApiCallMeter(NoOpExternalApiUsageMeter),
    )

    @Bean("clovaImageTextExtractor")
    fun clovaImageTextExtractor(
        @Qualifier("clovaOcrRestClient") restClient: RestClient,
        @Qualifier("ocrImageRestClient") imageRestClient: RestClient,
        properties: ClovaOcrProperties,
        callMeter: ObjectProvider<ExternalApiCallMeter>,
    ): ClovaImageTextExtractor = ClovaImageTextExtractor(
        restClient = restClient,
        objectMapper = jacksonObjectMapper(),
        properties = properties,
        imageDownloader = VisionImageDownloader(imageRestClient, properties.maxImageBytes),
        callMeter = callMeter.ifAvailable ?: ExternalApiCallMeter(NoOpExternalApiUsageMeter),
    )

    @Bean
    @Primary
    fun imageTextExtractor(
        configurationReader: RuntimeConfigurationReader,
        @Qualifier("corepinImageTextExtractor") corepin: ImageTextExtractor,
        @Qualifier("clovaImageTextExtractor") clova: ImageTextExtractor,
        @Qualifier("openAiImageTextExtractor") openAi: OpenAiImageTextExtractor,
    ): RuntimeImageTextExtractor = RuntimeImageTextExtractor(
        configurationReader,
        mapOf(
            OcrProviderType.COREPIN to corepin,
            OcrProviderType.CLOVA to clova,
            OcrProviderType.OPENAI to openAi,
        ),
    )
}
