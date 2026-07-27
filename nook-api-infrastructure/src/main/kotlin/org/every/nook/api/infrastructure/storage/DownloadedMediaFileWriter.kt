package org.every.nook.api.infrastructure.storage

import org.every.nook.api.application.post.error.PostMediaStorageException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

class DownloadedMediaFileWriter {
    fun persist(input: InputStream, contentType: String, extension: String, maxBytes: Long): DownloadedMedia {
        val path = createTempFile()
        return try {
            val digest = MessageDigest.getInstance(SHA_256)
            val size = input.use { source ->
                Files.newOutputStream(path).use { target ->
                    copyWithLimit(source, target, digest, maxBytes)
                }
            }
            DownloadedMedia(path, contentType, extension, HexFormat.of().formatHex(digest.digest()), size)
        } catch (exception: PostMediaStorageException) {
            runCatching { Files.deleteIfExists(path) }
            throw exception
        } catch (exception: IOException) {
            cleanupAndFail(path, exception)
        }
    }

    private fun createTempFile(): Path = try {
        Files.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX)
    } catch (exception: IOException) {
        storageFailure(exception)
    }

    private fun cleanupAndFail(path: Path, cause: Throwable): Nothing {
        runCatching { Files.deleteIfExists(path) }
        storageFailure(cause)
    }

    private fun copyWithLimit(source: InputStream, target: OutputStream, digest: MessageDigest, maxBytes: Long): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = source.read(buffer)
            if (read < 0) {
                return total
            }
            total += read
            if (total > maxBytes) {
                throw PostMediaStorageException()
            }
            digest.update(buffer, 0, read)
            target.write(buffer, 0, read)
        }
    }

    private fun storageFailure(cause: Throwable): Nothing = throw PostMediaStorageException(cause)

    private companion object {
        const val SHA_256 = "SHA-256"
        const val TEMP_FILE_PREFIX = "nook-media-"
        const val TEMP_FILE_SUFFIX = ".download"
        const val BUFFER_SIZE = 8192
    }
}
