package org.every.nook.api.infrastructure.config

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.every.nook.api.application.instagram.ExtractInstagramContentUseCase
import org.every.nook.api.application.instagram.InstagramContentProvider
import org.every.nook.api.infrastructure.instagram.BrightDataInstagramContentProvider
import org.every.nook.api.infrastructure.instagram.BrightDataInstagramMapper
import org.every.nook.api.infrastructure.instagram.BrightDataProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(BrightDataProperties::class)
class InstagramContentConfig {
    @Bean
    fun brightDataInstagramMapper(): BrightDataInstagramMapper = BrightDataInstagramMapper()

    @Bean
    fun brightDataRestClient(properties: BrightDataProperties): RestClient {
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
    fun instagramContentProvider(
        brightDataRestClient: RestClient,
        properties: BrightDataProperties,
        mapper: BrightDataInstagramMapper,
    ): InstagramContentProvider = BrightDataInstagramContentProvider(
        restClient = brightDataRestClient,
        objectMapper = jacksonObjectMapper(),
        properties = properties,
        mapper = mapper,
    )

    @Bean
    fun extractInstagramContentUseCase(provider: InstagramContentProvider): ExtractInstagramContentUseCase =
        ExtractInstagramContentUseCase(provider)
}
