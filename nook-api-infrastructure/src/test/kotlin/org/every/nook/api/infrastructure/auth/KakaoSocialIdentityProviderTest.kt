package org.every.nook.api.infrastructure.auth

import org.every.nook.api.application.auth.InvalidSocialCredentialException
import org.every.nook.api.application.auth.SocialCredential
import org.every.nook.api.application.auth.SocialLoginProvider
import org.every.nook.api.domain.member.SocialProvider
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KakaoSocialIdentityProviderTest {
    @Test
    fun `token issued for configured Kakao app is accepted`() {
        val builder = RestClient.builder().baseUrl("https://kapi.kakao.com")
        val server = MockRestServiceServer.bindTo(builder).build()
        val provider = KakaoSocialIdentityProvider(builder.build(), KakaoAuthProperties(appId = 123))
        server.expect(requestTo("https://kapi.kakao.com/v1/user/access_token_info"))
            .andRespond(withSuccess("""{"id":456,"app_id":123}""", MediaType.APPLICATION_JSON))

        val identity = provider.authenticate(
            SocialCredential(provider = SocialLoginProvider.KAKAO, accessToken = "access-token"),
        )

        assertEquals(SocialProvider.KAKAO, identity.provider)
        assertEquals("456", identity.subject)
        server.verify()
    }

    @Test
    fun `token issued for another Kakao app is rejected`() {
        val builder = RestClient.builder().baseUrl("https://kapi.kakao.com")
        val server = MockRestServiceServer.bindTo(builder).build()
        val provider = KakaoSocialIdentityProvider(builder.build(), KakaoAuthProperties(appId = 123))
        server.expect(requestTo("https://kapi.kakao.com/v1/user/access_token_info"))
            .andRespond(withSuccess("""{"id":456,"app_id":999}""", MediaType.APPLICATION_JSON))

        assertFailsWith<InvalidSocialCredentialException> {
            provider.authenticate(
                SocialCredential(provider = SocialLoginProvider.KAKAO, accessToken = "access-token"),
            )
        }
        server.verify()
    }
}
