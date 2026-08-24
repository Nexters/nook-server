package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.config.RuntimeConfigurationReader
import org.every.nook.api.application.content.PostContentExtractor
import org.every.nook.api.application.content.PostSourceResolver
import org.every.nook.api.infrastructure.instagram.ApifyInstagramMapper
import org.every.nook.api.infrastructure.instagram.ApifyInstagramPostContentExtractor
import org.every.nook.api.infrastructure.instagram.ApifyProperties
import org.every.nook.api.infrastructure.instagram.BrightDataInstagramMapper
import org.every.nook.api.infrastructure.instagram.BrightDataInstagramPostContentExtractor
import org.every.nook.api.infrastructure.instagram.BrightDataProperties
import org.every.nook.api.infrastructure.instagram.InstagramPostContentExtractor
import org.every.nook.api.infrastructure.instagram.InstagramPostSourceResolver
import org.every.nook.api.infrastructure.persistence.cache.ScrapingProviderResponseCache
import org.every.nook.api.infrastructure.providerusage.ExternalProviderUsageInterceptorFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper

@Configuration
@EnableConfigurationProperties(BrightDataProperties::class, ApifyProperties::class)
class InstagramContentConfig {
    @Bean
    fun instagramPostSourceResolver(): PostSourceResolver = InstagramPostSourceResolver()

    @Bean
    fun brightDataInstagramMapper(): BrightDataInstagramMapper = BrightDataInstagramMapper()

    @Bean
    fun apifyInstagramMapper(): ApifyInstagramMapper = ApifyInstagramMapper()

    @Bean
    fun brightDataRestClient(
        properties: BrightDataProperties,
        usage: ExternalProviderUsageInterceptorFactory,
    ): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.connectTimeout)
            setReadTimeout(properties.readTimeout)
        }
        return RestClient.builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(requestFactory)
            .requestInterceptor(usage.create("BRIGHT_DATA"))
            .build()
    }

    @Bean
    fun apifyRestClient(properties: ApifyProperties, usage: ExternalProviderUsageInterceptorFactory): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.connectTimeout)
            setReadTimeout(properties.readTimeout)
        }
        return RestClient.builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(requestFactory)
            .requestInterceptor(usage.create("APIFY"))
            .build()
    }

    @Bean
    fun instagramPostContentExtractor(
        @Qualifier("brightDataRestClient")
        brightDataRestClient: RestClient,
        @Qualifier("apifyRestClient")
        apifyRestClient: RestClient,
        brightDataProperties: BrightDataProperties,
        apifyProperties: ApifyProperties,
        brightDataMapper: BrightDataInstagramMapper,
        apifyMapper: ApifyInstagramMapper,
        responseCache: ScrapingProviderResponseCache,
        configurationReader: RuntimeConfigurationReader,
    ): PostContentExtractor {
        val objectMapper = jacksonObjectMapper()
        return InstagramPostContentExtractor(
            brightDataExtractor = BrightDataInstagramPostContentExtractor(
                restClient = brightDataRestClient,
                objectMapper = objectMapper,
                properties = brightDataProperties,
                mapper = brightDataMapper,
                responseCache = responseCache,
            ),
            apifyExtractor = ApifyInstagramPostContentExtractor(
                restClient = apifyRestClient,
                objectMapper = objectMapper,
                properties = apifyProperties,
                mapper = apifyMapper,
                responseCache = responseCache,
            ),
            configurationReader = configurationReader,
        )
    }
}
