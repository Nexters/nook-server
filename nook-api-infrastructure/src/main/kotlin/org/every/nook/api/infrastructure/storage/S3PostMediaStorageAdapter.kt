package org.every.nook.api.infrastructure.storage

import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.domain.post.PostMedia

class S3PostMediaStorageAdapter(
    private val downloader: RemoteMediaDownloader,
    private val objectStorage: MediaObjectStorage,
    private val properties: MediaStorageProperties,
) : PostMediaStoragePort {
    override fun store(media: PostMedia): PostMedia = downloader.download(media.url, media.type).use { downloaded ->
        val key = objectKey(downloaded)
        if (!objectStorage.exists(key)) {
            objectStorage.put(
                key = key,
                path = downloaded.path,
                contentType = downloaded.contentType,
                contentLength = downloaded.size,
            )
        }
        media.copy(url = "${properties.cloudFrontBaseUrl.trimEnd('/')}/$key")
    }

    private fun objectKey(media: DownloadedMedia): String =
        "$OBJECT_KEY_PREFIX/${media.sha256.take(HASH_PREFIX_LENGTH)}/${media.sha256}.${media.extension}"

    private companion object {
        const val OBJECT_KEY_PREFIX = "post-media/sha256"
        const val HASH_PREFIX_LENGTH = 2
    }
}
