package org.every.nook.api.infrastructure.config

import org.every.nook.api.infrastructure.vision.GoogleCloudVisionImageTextExtractor
import org.every.nook.api.infrastructure.vision.GoogleCloudVisionProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper

@Configuration
@EnableConfigurationProperties(GoogleCloudVisionProperties::class)
class GoogleCloudVisionConfig {
    @Bean("googleCloudVisionRestClient")
    fun googleCloudVisionRestClient(properties: GoogleCloudVisionProperties): RestClient {
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
    fun googleCloudVisionImageTextExtractor(
        @Qualifier("googleCloudVisionRestClient") restClient: RestClient,
        properties: GoogleCloudVisionProperties,
    ): GoogleCloudVisionImageTextExtractor = GoogleCloudVisionImageTextExtractor(
        restClient = restClient,
        objectMapper = jacksonObjectMapper(),
        properties = properties,
    )
}
