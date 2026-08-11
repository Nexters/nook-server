package org.every.nook.api.logging

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
@EnableConfigurationProperties(HttpLoggingProperties::class)
class HttpLoggingConfig {
    @Bean
    fun privacyArgumentFieldNames(): PrivacyArgumentFieldNames = PrivacyArgumentFieldNames.scan("org.every.nook.api")

    @Bean
    fun bodyLogFieldExtractor(
        properties: HttpLoggingProperties,
        objectMapper: ObjectMapper,
        privacyArgumentFieldNames: PrivacyArgumentFieldNames,
    ): BodyLogFieldExtractor = BodyLogFieldExtractor(properties, objectMapper, privacyArgumentFieldNames)

    @Bean
    fun requestParameterLogFieldExtractor(
        properties: HttpLoggingProperties,
        objectMapper: ObjectMapper,
        privacyArgumentFieldNames: PrivacyArgumentFieldNames,
    ): RequestParameterLogFieldExtractor =
        RequestParameterLogFieldExtractor(properties, objectMapper, privacyArgumentFieldNames)

    @Bean
    fun requestLoggingFilter(
        properties: HttpLoggingProperties,
        bodyLogFieldExtractor: BodyLogFieldExtractor,
        requestParameterLogFieldExtractor: RequestParameterLogFieldExtractor,
    ): RequestLoggingFilter = RequestLoggingFilter(properties, bodyLogFieldExtractor, requestParameterLogFieldExtractor)
}
