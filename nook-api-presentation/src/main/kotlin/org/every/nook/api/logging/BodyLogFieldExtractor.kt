package org.every.nook.api.logging

import org.springframework.http.MediaType
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale

class BodyLogFieldExtractor(
    private val properties: HttpLoggingProperties,
    private val objectMapper: ObjectMapper,
    private val privacyArgumentFieldNames: PrivacyArgumentFieldNames,
) {
    fun extract(prefix: String, body: ByteArray?, contentType: String?, charset: String?): Map<String, String> {
        if (!shouldExtract(body, contentType)) {
            return emptyMap()
        }

        requireNotNull(body)
        val fields = linkedMapOf<String, String>()
        val limitedBody = body.take(properties.body.maxBytes).toByteArray()
        val decodedBody = limitedBody.toString(resolveCharset(contentType, charset))
        fields["$prefix.size.bytes"] = body.size.toString()
        fields["$prefix.truncated"] = (body.size > properties.body.maxBytes).toString()

        try {
            val root = objectMapper.readTree(decodedBody)
            fields[prefix] = objectMapper.writeValueAsString(redactJson(root, sensitive = false))
        } catch (_: IllegalArgumentException) {
            fields["$prefix.parse_error"] = "true"
            fields[prefix] = decodedBody
        } catch (_: JacksonException) {
            fields["$prefix.parse_error"] = "true"
            fields[prefix] = decodedBody
        }

        return fields
    }

    fun hasIncludedContentType(contentType: String?): Boolean {
        if (contentType == null) {
            return false
        }
        val actual = runCatching { MediaType.parseMediaType(contentType) }.getOrNull() ?: return false
        return properties.body.includedContentTypes.any { configured ->
            val expected = MediaType.parseMediaType(configured)
            expected.includes(actual) || actual.includes(expected)
        }
    }

    private fun shouldExtract(body: ByteArray?, contentType: String?): Boolean =
        body != null && body.isNotEmpty() && hasIncludedContentType(contentType)

    private fun redactJson(node: JsonNode, sensitive: Boolean): JsonNode = when {
        sensitive -> JsonNodeFactory.instance.stringNode(MASKED_VALUE)
        node.isObject -> redactObject(node)
        node.isArray -> redactArray(node)
        else -> node
    }

    private fun redactObject(node: JsonNode): ObjectNode = JsonNodeFactory.instance.objectNode().apply {
        node.properties().forEach { (name, child) ->
            set(name, redactJson(child, sensitive = name.isSensitiveFieldName()))
        }
    }

    private fun redactArray(node: JsonNode): ArrayNode = JsonNodeFactory.instance.arrayNode().apply {
        node.forEach { child -> add(redactJson(child, sensitive = false)) }
    }

    private fun resolveCharset(contentType: String?, fallbackName: String?): Charset {
        val mediaType = contentType?.let { runCatching { MediaType.parseMediaType(it) }.getOrNull() }
        val isJson = mediaType?.isCompatibleWith(MediaType.APPLICATION_JSON) == true ||
            mediaType?.subtype?.endsWith("+json") == true
        if (isJson) {
            return mediaType.charset ?: StandardCharsets.UTF_8
        }
        return fallbackName?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: StandardCharsets.UTF_8
    }

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
