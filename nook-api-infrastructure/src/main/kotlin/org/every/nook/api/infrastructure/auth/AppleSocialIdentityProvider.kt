package org.every.nook.api.infrastructure.auth

import org.every.nook.api.application.auth.InvalidSocialCredentialException
import org.every.nook.api.application.auth.SocialCredential
import org.every.nook.api.application.auth.SocialIdentity
import org.every.nook.api.domain.member.SocialProvider
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode

class AppleSocialIdentityProvider(
    private val restClient: RestClient,
    private val jwtDecoder: JwtDecoder,
    private val clientSecretGenerator: AppleClientSecretGenerator,
    private val properties: AppleAuthProperties,
) {
    fun authenticate(credential: SocialCredential): SocialIdentity {
        val authorizationCode = required(credential.authorizationCode)
        val suppliedIdentityToken = required(credential.identityToken)
        val exchanged = decode(exchange(authorizationCode))
        val supplied = decode(suppliedIdentityToken)
        if (exchanged.subject != supplied.subject) throw InvalidSocialCredentialException()
        return SocialIdentity(SocialProvider.APPLE, exchanged.subject)
    }

    private fun exchange(authorizationCode: String): String {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("client_id", properties.clientId)
            add("client_secret", clientSecret())
            add("code", authorizationCode)
            add("grant_type", "authorization_code")
        }
        val response = exchangeRequest(form)
        return response.path("id_token").asText().takeIf(String::isNotBlank)
            ?: throw InvalidSocialCredentialException()
    }

    private fun clientSecret(): String = runCatching(clientSecretGenerator::generate)
        .getOrElse { throw InvalidSocialCredentialException() }

    private fun exchangeRequest(form: LinkedMultiValueMap<String, String>): JsonNode = runCatching {
        restClient.post()
            .uri("/auth/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(JsonNode::class.java)
    }.getOrElse { throw InvalidSocialCredentialException() }
        ?: throw InvalidSocialCredentialException()

    private fun decode(token: String) = runCatching { jwtDecoder.decode(token) }
        .getOrElse { throw InvalidSocialCredentialException() }

    private fun required(value: String?): String = value?.takeIf(String::isNotBlank)
        ?: throw InvalidSocialCredentialException()
}
