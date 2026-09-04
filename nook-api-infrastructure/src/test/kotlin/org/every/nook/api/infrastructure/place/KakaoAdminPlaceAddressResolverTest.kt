package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.admin.AdminPlaceAddressProviderException
import org.hamcrest.Matchers.containsString
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class KakaoAdminPlaceAddressResolverTest {
    @Test
    fun `prefers canonical road address and maps coordinates`() {
        val fixture = fixture()
        fixture.server.expect(requestTo(containsString("/v2/local/search/address.json")))
            .andExpect(requestTo(containsString("query=%EC%84%9C%EC%9A%B8")))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "KakaoAK test-rest-api-key"))
            .andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON))

        val result = fixture.resolver.resolve("서울 성동구 성수동 1")

        assertEquals("서울특별시 성동구 아차산로 1", result?.address)
        assertEquals("37.5120741", result?.latitude?.toPlainString())
        assertEquals("127.0590297", result?.longitude?.toPlainString())
        fixture.server.verify()
    }

    @Test
    fun `returns null when address does not resolve to exactly one result`() {
        val fixture = fixture()
        fixture.server.expect(requestTo(containsString("/v2/local/search/address.json")))
            .andRespond(withSuccess("""{"meta":{"total_count":0},"documents":[]}""", MediaType.APPLICATION_JSON))

        assertNull(fixture.resolver.resolve("없는 주소"))
    }

    @Test
    fun `converts provider failure to application exception`() {
        val fixture = fixture()
        fixture.server.expect(requestTo(containsString("/v2/local/search/address.json")))
            .andRespond(withServerError())

        assertFailsWith<AdminPlaceAddressProviderException> { fixture.resolver.resolve("서울") }
    }

    private fun fixture(): Fixture {
        val builder = RestClient.builder().baseUrl("https://dapi.kakao.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        return Fixture(
            KakaoAdminPlaceAddressResolver(
                builder.build(),
                jacksonObjectMapper(),
                KakaoPlaceProperties(restApiKey = "test-rest-api-key"),
            ),
            server,
        )
    }

    private data class Fixture(val resolver: KakaoAdminPlaceAddressResolver, val server: MockRestServiceServer)

    private companion object {
        val SUCCESS_RESPONSE =
            """
            {
              "meta": {"total_count": 1},
              "documents": [{
                "address_name": "서울 성동구 성수동 1",
                "x": "127.0590297",
                "y": "37.5120741",
                "road_address": {"address_name": "서울특별시 성동구 아차산로 1"}
              }]
            }
            """.trimIndent()
    }
}
