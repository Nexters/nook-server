package org.every.nook.api.infrastructure.storage

import java.nio.file.Files
import java.nio.file.Path

data class DownloadedMedia(
    val path: Path,
    val contentType: String,
    val extension: String,
    val sha256: String,
    val size: Long,
) : AutoCloseable {
    override fun close() {
        Files.deleteIfExists(path)
    }
}

fun interface RemoteMediaDownloader {
    fun download(sourceUrl: String, expectedType: org.every.nook.api.domain.post.PostMedia.MediaType): DownloadedMedia
}
