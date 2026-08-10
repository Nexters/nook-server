package org.every.nook.api.application.member.port

import java.time.Instant

fun interface ProfileImageUploadPort {
    fun create(command: ProfileImageUploadCommand): ProfileImageUpload
}

data class ProfileImageUploadCommand(val memberId: Long, val contentType: String)

data class ProfileImageUpload(
    val uploadUrl: String,
    val profileImageUrl: String,
    val contentType: String,
    val expiresAt: Instant,
    val maxBytes: Long,
)
