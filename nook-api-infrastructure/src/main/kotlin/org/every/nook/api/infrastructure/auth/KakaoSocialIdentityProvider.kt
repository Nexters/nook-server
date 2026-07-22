package org.every.nook.api.infrastructure.auth

import org.every.nook.api.application.auth.InvalidSocialCredentialException
import org.every.nook.api.application.auth.SocialCredential
import org.every.nook.api.application.auth.SocialIdentity
import org.every.nook.api.domain.member.SocialProvider
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode

class KakaoSocialIdentityProvider(private val restClient: RestClient, private val properties: KakaoAuthProperties) {
    fun authenticate(credential: SocialCredential): SocialIdentity {
        val accessToken = required(credential.accessToken)
        val response = requestTokenInfo(accessToken)
        val appId = response.path("app_id").asLong()
        if (appId != properties.appId) throw InvalidSocialCredentialException()
        val subject = response.path("id").asText().takeIf(String::isNotBlank)
            ?: throw InvalidSocialCredentialException()
        return SocialIdentity(SocialProvider.KAKAO, subject)
    }

    private fun requestTokenInfo(accessToken: String): JsonNode = runCatching {
        restClient.get()
            .uri("/v1/user/access_token_info")
            .headers { it.setBearerAuth(accessToken) }
            .retrieve()
            .body(JsonNode::class.java)
    }.getOrElse { throw InvalidSocialCredentialException() }
        ?: throw InvalidSocialCredentialException()

    private fun required(value: String?): String = value?.takeIf(String::isNotBlank)
        ?: throw InvalidSocialCredentialException()
}
