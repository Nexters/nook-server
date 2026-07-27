package org.every.nook.api.infrastructure.storage

import org.every.nook.api.application.post.error.PostMediaStorageException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.nio.file.Path

class S3MediaObjectStorage(private val s3Client: S3Client, private val properties: MediaStorageProperties) :
    MediaObjectStorage {
    override fun exists(key: String): Boolean = try {
        s3Client.headObject(
            HeadObjectRequest.builder()
                .bucket(properties.bucket)
                .key(key)
                .build(),
        )
        true
    } catch (exception: S3Exception) {
        if (exception.statusCode() == NOT_FOUND_STATUS) {
            false
        } else {
            throw PostMediaStorageException(exception)
        }
    }

    override fun put(key: String, path: Path, contentType: String, contentLength: Long) {
        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(properties.bucket)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .cacheControl(CACHE_CONTROL)
                    .build(),
                RequestBody.fromFile(path),
            )
        } catch (exception: S3Exception) {
            throw PostMediaStorageException(exception)
        }
    }

    private companion object {
        const val NOT_FOUND_STATUS = 404
        const val CACHE_CONTROL = "public, max-age=31536000, immutable"
    }
}
