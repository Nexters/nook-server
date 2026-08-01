package org.every.nook.api.infrastructure.storage

import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.infrastructure.persistence.cache.MediaUrlCacheEntity
import org.every.nook.api.infrastructure.persistence.cache.MediaUrlCacheJpaRepository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class S3PostMediaStorageAdapter(
    private val downloader: RemoteMediaDownloader,
    private val objectStorage: MediaObjectStorage,
    private val properties: MediaStorageProperties,
    private val cacheRepository: MediaUrlCacheJpaRepository,
) : PostMediaStoragePort {
    override fun store(media: PostMedia): PostMedia {
        val sourceUrlHash = media.url.sha256()
        cacheRepository.findBySourceUrlHash(sourceUrlHash)?.let { return media.copy(url = it.storedUrl) }
        return downloader.download(media.url, media.type).use { downloaded ->
            val key = objectKey(downloaded)
            if (!objectStorage.exists(key)) {
                objectStorage.put(
                    key = key,
                    path = downloaded.path,
                    contentType = downloaded.contentType,
                    contentLength = downloaded.size,
                )
            }
            val storedUrl = "${properties.cloudFrontBaseUrl.trimEnd('/')}/$key"
            runCatching {
                cacheRepository.saveAndFlush(MediaUrlCacheEntity(sourceUrlHash, media.url, storedUrl))
            }
                .onFailure { exception ->
                    if (cacheRepository.findBySourceUrlHash(sourceUrlHash) == null) throw exception
                }
            media.copy(url = cacheRepository.findBySourceUrlHash(sourceUrlHash)?.storedUrl ?: storedUrl)
        }
    }

    private fun objectKey(media: DownloadedMedia): String =
        "$OBJECT_KEY_PREFIX/${media.sha256.take(HASH_PREFIX_LENGTH)}/${media.sha256}.${media.extension}"

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val OBJECT_KEY_PREFIX = "post-media/sha256"
        const val HASH_PREFIX_LENGTH = 2
    }
}
