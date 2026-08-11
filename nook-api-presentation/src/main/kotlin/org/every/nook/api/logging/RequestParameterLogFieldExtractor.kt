package org.every.nook.api.logging

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.servlet.HandlerMapping
import tools.jackson.databind.ObjectMapper
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class RequestParameterLogFieldExtractor(
    private val properties: HttpLoggingProperties,
    private val objectMapper: ObjectMapper,
    private val privacyArgumentFieldNames: PrivacyArgumentFieldNames,
) {
    fun queryParams(request: HttpServletRequest): String? {
        val query = request.queryString?.takeIf { it.isNotBlank() } ?: return null
        val parameters = linkedMapOf<String, MutableList<String>>()

        query.split('&').filter { it.isNotEmpty() }.forEach { argument ->
            val parts = argument.split('=', limit = 2)
            val name = parts.first().decode().takeIf { it.isNotBlank() } ?: return@forEach
            val value = parts.getOrElse(1) { "" }.decode()
            parameters.getOrPut(name) { mutableListOf() }
                .add(if (name.isSensitiveFieldName()) MASKED_VALUE else value)
        }

        return parameters.takeIf { it.isNotEmpty() }?.toJson()
    }

    fun pathParams(request: HttpServletRequest): String? {
        val variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<*, *>
            ?: return null
        val parameters = variables.entries.mapNotNull { (rawName, rawValue) ->
            val name = rawName as? String ?: return@mapNotNull null
            val value = rawValue?.toString() ?: return@mapNotNull null
            name to if (name.isSensitiveFieldName()) MASKED_VALUE else value
        }.toMap(linkedMapOf())
        return parameters.takeIf { it.isNotEmpty() }?.toJson()
    }

    private fun Any.toJson(): String? = runCatching { objectMapper.writeValueAsString(this) }.getOrNull()

    private fun String.decode(): String =
        runCatching { URLDecoder.decode(this, StandardCharsets.UTF_8) }.getOrDefault(this)

    private fun String.isSensitiveFieldName(): Boolean {
        val normalized = toLogFieldName().replace("_", "")
        return privacyArgumentFieldNames.contains(this) ||
            properties.body.sensitiveFieldKeywords.any { normalized.contains(it.lowercase(Locale.ROOT)) }
    }

    private fun String.toLogFieldName(): String = lowercase(Locale.ROOT)
        .replace('-', '_')
        .replace(Regex("[^a-z0-9_.]"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')

    private companion object {
        const val MASKED_VALUE = "****"
    }
}
