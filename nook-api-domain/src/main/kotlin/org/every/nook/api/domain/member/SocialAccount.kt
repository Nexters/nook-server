package org.every.nook.api.domain.member

data class SocialAccount(
    val id: Long? = null,
    val memberId: Long,
    val provider: SocialProvider,
    val providerSubject: String,
) {
    init {
        require(providerSubject.isNotBlank()) { "Provider subject must not be blank" }
    }
}
