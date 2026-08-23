package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.billing.NoOpExternalApiUsageMeter
import org.every.nook.api.infrastructure.billing.ExternalApiCallMeter
import org.every.nook.api.infrastructure.openai.OpenAiContentInferenceAdapter
import org.every.nook.api.infrastructure.openai.OpenAiCoverTitleExtractor
import org.every.nook.api.infrastructure.openai.OpenAiImageTextExtractor
import org.every.nook.api.infrastructure.openai.OpenAiPostTitleSelector
import org.every.nook.api.infrastructure.openai.OpenAiProperties
import org.every.nook.api.infrastructure.openai.OpenAiRateLimitInterceptor
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper

@Configuration
@EnableConfigurationProperties(OpenAiProperties::class)
class OpenAiConfig {
    @Bean("openAiRestClient")
    fun openAiRestClient(properties: OpenAiProperties): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.connectTimeout)
            setReadTimeout(properties.readTimeout)
        }
        return RestClient.builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(requestFactory)
            .requestInterceptor(
                OpenAiRateLimitInterceptor(
                    maxConcurrentRequests = properties.maxConcurrentRequests,
                    retryBackoffs = properties.rateLimitRetryBackoffs,
                ),
            )
            .build()
    }

    @Bean
    fun openAiContentInferenceAdapter(
        @Qualifier("openAiRestClient") restClient: RestClient,
        properties: OpenAiProperties,
        callMeter: ObjectProvider<ExternalApiCallMeter>,
    ): OpenAiContentInferenceAdapter = OpenAiContentInferenceAdapter(
        restClient = restClient,
        objectMapper = jacksonObjectMapper(),
        properties = properties,
        callMeter = callMeter.ifAvailable ?: ExternalApiCallMeter(NoOpExternalApiUsageMeter),
    )

    @Bean
    fun openAiCoverTitleExtractor(
        @Qualifier("openAiRestClient") restClient: RestClient,
        properties: OpenAiProperties,
        callMeter: ObjectProvider<ExternalApiCallMeter>,
    ): OpenAiCoverTitleExtractor = OpenAiCoverTitleExtractor(
        restClient = restClient,
        objectMapper = jacksonObjectMapper(),
        properties = properties,
        callMeter = callMeter.ifAvailable ?: ExternalApiCallMeter(NoOpExternalApiUsageMeter),
    )

    @Bean
    fun openAiPostTitleSelector(
        @Qualifier("openAiRestClient") restClient: RestClient,
        properties: OpenAiProperties,
        callMeter: ObjectProvider<ExternalApiCallMeter>,
    ): OpenAiPostTitleSelector = OpenAiPostTitleSelector(
        restClient = restClient,
        objectMapper = jacksonObjectMapper(),
        properties = properties,
        callMeter = callMeter.ifAvailable ?: ExternalApiCallMeter(NoOpExternalApiUsageMeter),
    )

    @Bean("openAiImageTextExtractor")
    fun openAiImageTextExtractor(
        @Qualifier("openAiRestClient") restClient: RestClient,
        properties: OpenAiProperties,
        callMeter: ObjectProvider<ExternalApiCallMeter>,
    ): OpenAiImageTextExtractor = OpenAiImageTextExtractor(
        restClient = restClient,
        objectMapper = jacksonObjectMapper(),
        properties = properties,
        callMeter = callMeter.ifAvailable ?: ExternalApiCallMeter(NoOpExternalApiUsageMeter),
    )
}
