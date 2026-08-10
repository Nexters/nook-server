package org.every.nook.api.infrastructure.storage

import org.every.nook.api.application.member.ProfileImageUploadUnavailableException
import org.every.nook.api.application.member.port.ProfileImageUpload
import org.every.nook.api.application.member.port.ProfileImageUploadCommand
import org.every.nook.api.application.member.port.ProfileImageUploadPort
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "external.media-storage",
    name = ["enabled"],
    havingValue = "false",
    matchIfMissing = true,
)
class UnavailableProfileImageUploadAdapter : ProfileImageUploadPort {
    override fun create(command: ProfileImageUploadCommand): ProfileImageUpload =
        throw ProfileImageUploadUnavailableException()
}
