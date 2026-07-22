package org.every.nook.api.domain.member

private const val MIN_NICKNAME_LENGTH = 2
private const val MAX_NICKNAME_LENGTH = 20
private const val MAX_PROFILE_IMAGE_URL_LENGTH = 2048

data class Member(
    val id: Long? = null,
    val nickname: String,
    val profileImageUrl: String?,
    val status: MemberStatus = MemberStatus.ACTIVE,
) {
    init {
        require(nickname == nickname.trim()) { "Nickname must not have surrounding whitespace" }
        require(nickname.length in MIN_NICKNAME_LENGTH..MAX_NICKNAME_LENGTH) {
            "Nickname length must be between $MIN_NICKNAME_LENGTH and $MAX_NICKNAME_LENGTH"
        }
        require(profileImageUrl == null || isValidProfileImageUrl(profileImageUrl)) {
            "Profile image URL must be a valid HTTPS URL"
        }
    }

    companion object {
        fun normalizeNickname(nickname: String): String = nickname.trim()

        private fun isValidProfileImageUrl(url: String): Boolean = url.length <= MAX_PROFILE_IMAGE_URL_LENGTH &&
            runCatching {
                val uri = java.net.URI(url)
                uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
            }.getOrDefault(false)
    }
}
