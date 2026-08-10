package org.every.nook.api.infrastructure.storage

import org.every.nook.api.application.member.ProfileImageUploadUnavailableException
import org.every.nook.api.application.member.port.ProfileImageUpload
import org.every.nook.api.application.member.port.ProfileImageUploadCommand
import org.every.nook.api.application.member.port.ProfileImageUploadPort
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Clock
import java.time.Instant
import java.util.UUID

class S3ProfileImageUploadAdapter(
    private val presigner: S3Presigner,
    private val properties: MediaStorageProperties,
    private val clock: Clock,
) : ProfileImageUploadPort {
    override fun create(command: ProfileImageUploadCommand): ProfileImageUpload {
        val contentType = command.contentType.lowercase()
        val key = objectKey(command.memberId, contentType)
        val expiresAt = Instant.now(clock).plus(properties.profileImageUploadExpires)
        val request = PutObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key)
            .contentType(contentType)
            .cacheControl(CACHE_CONTROL)
            .build()
        val presigned = presigner.presignPutObject(
            PutObjectPresignRequest.builder()
                .signatureDuration(properties.profileImageUploadExpires)
                .putObjectRequest(request)
                .build(),
        )
        return ProfileImageUpload(
            uploadUrl = presigned.url().toString(),
            profileImageUrl = "${properties.cloudFrontBaseUrl.trimEnd('/')}/$key",
            contentType = contentType,
            expiresAt = expiresAt,
            maxBytes = properties.maxImageBytes,
        )
    }

    private fun objectKey(memberId: Long, contentType: String): String =
        "$OBJECT_KEY_PREFIX/$memberId/${UUID.randomUUID()}.${contentType.extension()}"

    private fun String.extension(): String = when (this) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        else -> throw ProfileImageUploadUnavailableException()
    }

    private companion object {
        const val OBJECT_KEY_PREFIX = "profile-images"
        const val CACHE_CONTROL = "public, max-age=31536000, immutable"
    }
}
