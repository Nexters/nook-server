package org.every.nook.api.logging

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
@EnableConfigurationProperties(HttpLoggingProperties::class)
class HttpLoggingConfig {
    @Bean
    fun bodyLogFieldExtractor(properties: HttpLoggingProperties, objectMapper: ObjectMapper): BodyLogFieldExtractor =
        BodyLogFieldExtractor(properties, objectMapper)

    @Bean
    fun requestLoggingFilter(
        properties: HttpLoggingProperties,
        bodyLogFieldExtractor: BodyLogFieldExtractor,
    ): RequestLoggingFilter = RequestLoggingFilter(properties, bodyLogFieldExtractor)
}
