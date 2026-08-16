package org.every.nook.api.infrastructure.config

import org.every.nook.api.infrastructure.openai.OpenAiContentInferenceAdapter
import org.every.nook.api.infrastructure.openai.OpenAiImageTextExtractor
import org.every.nook.api.infrastructure.openai.OpenAiProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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
            .build()
    }

    @Bean
    fun openAiContentInferenceAdapter(
        @Qualifier("openAiRestClient") restClient: RestClient,
        properties: OpenAiProperties,
    ): OpenAiContentInferenceAdapter = OpenAiContentInferenceAdapter(
        restClient = restClient,
        objectMapper = jacksonObjectMapper(),
        properties = properties,
    )

    @Bean
    @ConditionalOnProperty(
        prefix = "place-parsing",
        name = ["image-text-provider"],
        havingValue = "openai",
        matchIfMissing = true,
    )
    fun openAiImageTextExtractor(
        @Qualifier("openAiRestClient") restClient: RestClient,
        properties: OpenAiProperties,
    ): OpenAiImageTextExtractor = OpenAiImageTextExtractor(
        restClient = restClient,
        objectMapper = jacksonObjectMapper(),
        properties = properties,
    )
}
