package org.every.nook.api.infrastructure.storage

import org.every.nook.api.application.post.error.PostMediaStorageException
import org.every.nook.api.domain.post.PostMedia
import java.net.URI
import java.util.Locale

class JdkRemoteMediaDownloader(
    private val httpClient: RemoteMediaHttpClient,
    private val properties: MediaStorageProperties,
    private val uriValidator: PublicHttpsUriValidator,
    private val fileWriter: DownloadedMediaFileWriter = DownloadedMediaFileWriter(),
) : RemoteMediaDownloader {
    override fun download(sourceUrl: String, expectedType: PostMedia.MediaType): DownloadedMedia {
        var uri = uriValidator.validate(sourceUrl)
        repeat(properties.maxRedirects + 1) { redirectCount ->
            val response = send(uri)
            if (response.statusCode in REDIRECT_STATUSES) {
                response.body.close()
                if (redirectCount == properties.maxRedirects) {
                    throw PostMediaStorageException()
                }
                uri = redirectedUri(uri, response)
            } else {
                return readMedia(response, expectedType)
            }
        }
        throw PostMediaStorageException()
    }

    private fun send(uri: URI): MediaHttpResponse = httpClient.get(uri)

    private fun redirectedUri(current: URI, response: MediaHttpResponse): URI {
        val location = response.firstHeader(LOCATION_HEADER) ?: throw PostMediaStorageException()
        return uriValidator.validate(current.resolve(location).toString())
    }

    private fun readMedia(response: MediaHttpResponse, expectedType: PostMedia.MediaType): DownloadedMedia {
        if (response.statusCode !in SUCCESS_STATUS_RANGE) {
            response.body.close()
            throw PostMediaStorageException()
        }
        val contentType = normalizedContentType(response)
        val format = MEDIA_FORMATS[contentType]
            ?.takeIf { it.mediaType == expectedType }
            ?: run {
                response.body.close()
                throw PostMediaStorageException()
            }
        val maxBytes = maxBytes(expectedType)
        validateContentLength(response, maxBytes)
        return fileWriter.persist(response.body, contentType, format.extension, maxBytes)
    }

    private fun normalizedContentType(response: MediaHttpResponse): String {
        val contentType = response.firstHeader(CONTENT_TYPE_HEADER)
        if (contentType == null) {
            response.body.close()
            throw PostMediaStorageException()
        }
        return contentType.substringBefore(';').trim().lowercase(Locale.ROOT)
    }

    private fun validateContentLength(response: MediaHttpResponse, maxBytes: Long) {
        val length = response.firstHeader(CONTENT_LENGTH_HEADER)?.toLongOrNull() ?: return
        if (length <= 0 || length > maxBytes) {
            response.body.close()
            throw PostMediaStorageException()
        }
    }

    private fun maxBytes(mediaType: PostMedia.MediaType): Long = when (mediaType) {
        PostMedia.MediaType.IMAGE -> properties.maxImageBytes
        PostMedia.MediaType.VIDEO -> properties.maxVideoBytes
    }

    private data class MediaFormat(val mediaType: PostMedia.MediaType, val extension: String)

    private companion object {
        const val LOCATION_HEADER = "Location"
        const val CONTENT_TYPE_HEADER = "Content-Type"
        const val CONTENT_LENGTH_HEADER = "Content-Length"
        val SUCCESS_STATUS_RANGE = 200..299
        val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
        val MEDIA_FORMATS = mapOf(
            "image/jpeg" to MediaFormat(PostMedia.MediaType.IMAGE, "jpg"),
            "image/png" to MediaFormat(PostMedia.MediaType.IMAGE, "png"),
            "image/webp" to MediaFormat(PostMedia.MediaType.IMAGE, "webp"),
            "image/gif" to MediaFormat(PostMedia.MediaType.IMAGE, "gif"),
            "video/mp4" to MediaFormat(PostMedia.MediaType.VIDEO, "mp4"),
            "video/quicktime" to MediaFormat(PostMedia.MediaType.VIDEO, "mov"),
        )
    }
}
