package org.every.nook.api.logging

import org.springframework.http.MediaType
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale

class BodyLogFieldExtractor(private val properties: HttpLoggingProperties, private val objectMapper: ObjectMapper) {
    fun extract(prefix: String, body: ByteArray?, contentType: String?, charset: String?): Map<String, String> {
        if (!shouldExtract(body, contentType)) {
            return emptyMap()
        }

        requireNotNull(body)
        val fields = linkedMapOf<String, String>()
        val limitedBody = body.take(properties.body.maxBytes).toByteArray()
        fields["$prefix.size.bytes"] = body.size.toString()
        fields["$prefix.truncated"] = (body.size > properties.body.maxBytes).toString()

        try {
            val root = objectMapper.readTree(limitedBody.toString(charset(charset)))
            flattenJson(prefix, root, fields, depth = 0, sensitive = false)
        } catch (_: IllegalArgumentException) {
            fields["$prefix.parse_error"] = "true"
        } catch (_: JacksonException) {
            fields["$prefix.parse_error"] = "true"
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

    private fun flattenJson(
        prefix: String,
        node: JsonNode,
        output: MutableMap<String, String>,
        depth: Int,
        sensitive: Boolean,
    ) {
        if (output.size >= properties.body.maxFlattenedFields) {
            output["$prefix.fields_truncated"] = "true"
            return
        }
        if (depth >= properties.body.maxDepth || node.isValueNode || node.isNull) {
            output[prefix] = if (sensitive) MASKED_VALUE else node.asString("")
            return
        }

        when {
            node.isObject -> node.properties().forEach { (name, child) ->
                flattenJson(
                    prefix = "$prefix.${name.toLogFieldName()}",
                    node = child,
                    output = output,
                    depth = depth + 1,
                    sensitive = sensitive || name.isSensitiveFieldName(),
                )
            }

            node.isArray -> node.take(properties.body.maxArrayItems).forEachIndexed { index, child ->
                flattenJson("$prefix.$index", child, output, depth + 1, sensitive)
            }

            else -> output[prefix] = node.toString()
        }
    }

    private fun charset(name: String?): Charset =
        name?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: StandardCharsets.UTF_8

    private fun String.isSensitiveFieldName(): Boolean {
        val normalized = toLogFieldName().replace("_", "")
        return properties.body.sensitiveFieldKeywords.any { normalized.contains(it.lowercase(Locale.ROOT)) }
    }

    private fun String.toLogFieldName(): String = lowercase(Locale.ROOT)
        .replace('-', '_')
        .replace(Regex("[^a-z0-9_.]"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')

    private companion object {
        const val MASKED_VALUE = "[REDACTED]"
    }
}
