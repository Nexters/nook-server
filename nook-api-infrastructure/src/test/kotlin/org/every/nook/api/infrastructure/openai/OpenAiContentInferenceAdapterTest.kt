package org.every.nook.api.infrastructure.openai

import org.every.nook.api.application.place.ImageTextExtractor
import org.every.nook.api.application.place.ImageTranscript
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceCandidateSelector
import org.every.nook.api.application.place.PlaceClue
import org.every.nook.api.application.place.PlaceClueEvidence
import org.every.nook.api.application.place.PlaceClueExtractor
import org.every.nook.api.application.place.PlaceTagExtractor
import org.every.nook.api.application.post.CoverTitleExtractor
import org.every.nook.api.application.post.PostContentInference
import org.every.nook.api.domain.place.PlaceTag
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpenAiContentInferenceAdapterTest {
    @Test
    fun `extracts grounded place tags from the controlled vocabulary`() {
        val fixture = adapterFixture()
        fixture.server.expect(requestTo("https://api.openai.test/v1/responses"))
            .andExpect(content().string(containsString("place_tags")))
            .andExpect(content().string(containsString("SOLO_DINING")))
            .andExpect(content().string(not(containsString("input_image"))))
            .andExpect(content().string(containsString("placeIndex")))
            .andExpect(content().string(containsString("혼자 먹기 좋고 조용해요")))
            .andRespond(
                withSuccess(
                    response(
                        """
                        {"places":[{"placeIndex":0,"tags":[
                            {"tag":"QUIET","confidence":0.92,"evidenceSource":"BODY","evidenceText":"조용해요"},
                            {"tag":"SOLO_DINING","confidence":0.88,"evidenceSource":"BODY","evidenceText":"혼자 먹기 좋아요"}
                        ]}]}
                        """.trimIndent(),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val tags = fixture.adapter.extract(
            PlaceTagExtractor.Request(
                places = listOf(
                    PlaceTagExtractor.PlaceInput(
                        placeIndex = 0,
                        place = candidate(),
                        body = "혼자 먹기 좋고 조용해요",
                        hashtags = listOf("혼밥"),
                        candidateTags = PlaceTag.defaultDefinitions.filter {
                            it.tag == PlaceTag.QUIET.name || it.tag == PlaceTag.SOLO_DINING.name
                        },
                    ),
                ),
            ),
        )

        assertEquals(listOf(PlaceTag.QUIET.name, PlaceTag.SOLO_DINING.name), tags.single().tags.map { it.tag })
        assertEquals(0.92, tags.single().tags.first().confidence)
        fixture.server.verify()
    }

    @Test
    fun `infers a title and text place clues with one structured output`() {
        val fixture = adapterFixture()
        fixture.server.expect(requestTo("https://api.openai.test/v1/responses"))
            .andExpect(header("Authorization", "Bearer test-key"))
            .andExpect(content().string(containsString("post_content_inference")))
            .andExpect(content().string(containsString("홍보성")))
            .andExpect(content().string(containsString("방문해보기 좋은 곳")))
            .andExpect(content().string(not(containsString("홍별감네"))))
            .andExpect(content().string(containsString("\"maxLength\":25")))
            .andExpect(content().string(containsString("\"maxItems\":60")))
            .andRespond(
                withSuccess(
                    response(
                        """
                        {
                          "title":"용산 미나리 삼겹살",
                          "places":[{
                            "name":"원동미나리삼겹살",
                            "region":"용산구",
                            "queries":["용산 원동미나리삼겹살"],
                            "evidence":[]
                          }]
                        }
                        """.trimIndent(),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val inference = fixture.adapter.infer(
            PostContentInference.Request("원동미나리삼겹살 방문", listOf("용산맛집"), "용산구"),
        )

        assertEquals("용산 미나리 삼겹살", inference.title)
        assertEquals(listOf("원동미나리삼겹살"), inference.placeClues.map(PlaceClue::name))
        fixture.server.verify()
    }

    @Test
    fun `extracts an exact title and date label from a cover image`() {
        val fixture = adapterFixture()
        fixture.server.expect(requestTo("https://api.openai.test/v1/responses"))
            .andExpect(content().string(containsString("\"type\":\"input_image\"")))
            .andExpect(content().string(containsString(TEST_IMAGE_URLS.first())))
            .andExpect(content().string(containsString("\"detail\":\"high\"")))
            .andExpect(content().string(containsString("post_cover_title")))
            .andExpect(content().string(containsString("titleLabel")))
            .andExpect(content().string(containsString("계정명·로고")))
            .andRespond(
                withSuccess(
                    response(
                        """
                        {"titleLabel":"6월 2주차","title":"요즘 뜨고 있는 금주의 신상스폿"}
                        """.trimIndent(),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val title = fixture.coverTitleExtractor.extract(
            CoverTitleExtractor.Request(TEST_IMAGE_URLS.first()),
        )

        assertEquals("6월 2주차 요즘 뜨고 있는 금주의 신상스폿", title)
        fixture.server.verify()
    }

    @Test
    fun `extracts multiple grounded place clues`() {
        val fixture = adapterFixture()
        fixture.server.expect(requestTo("https://api.openai.test/v1/responses"))
            .andExpect(content().string(containsString("place_clues")))
            .andExpect(content().string(containsString("\"maxItems\":60")))
            .andRespond(
                withSuccess(
                    response(
                        """
                        {
                          "places": [
                            {
                              "name":"원동미나리삼겹살",
                              "region":"용산구",
                              "queries":["용산 원동미나리삼겹살"],
                              "evidence":[]
                            },
                            {"name":"서울역","region":null,"queries":["서울역"],"evidence":[]}
                          ]
                        }
                        """.trimIndent(),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val places = fixture.adapter.extract(PlaceClueExtractor.Request("두 장소 방문", emptyList(), null))

        assertEquals(listOf("원동미나리삼겹살", "서울역"), places.map { it.name })
        assertEquals(null, places.last().region)
    }

    @Test
    fun `transcribes every image with its global index in one high detail request`() {
        val fixture = adapterFixture()
        val imageUrls = TEST_IMAGE_URLS.take(2)
        fixture.server.expect(requestTo("https://api.openai.test/v1/responses"))
            .andExpect(content().string(containsString("\"type\":\"input_image\"")))
            .andExpect(content().string(containsString(imageUrls.first())))
            .andExpect(content().string(containsString(imageUrls.last())))
            .andExpect(content().string(containsString("\"detail\":\"high\"")))
            .andExpect(content().string(containsString("\"model\":\"gpt-5-mini\"")))
            .andExpect(content().string(containsString("\"max_output_tokens\":4000")))
            .andExpect(content().string(containsString("판단, 요약, 번역, 맞춤법 교정")))
            .andExpect(content().string(containsString("imageIndex")))
            .andRespond(
                withSuccess(
                    response(
                        """
                        {
                          "images": [
                            {
                              "imageIndex":2,
                              "texts":["빈브라더스 커피하우스 서울","서울 마포구 상수동 354-12"]
                            },
                            {
                              "imageIndex":3,
                              "texts":["누뗀","서울 서초구 신원동 489-7"]
                            }
                          ]
                        }
                        """.trimIndent(),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val transcripts = fixture.imageTextExtractor.extract(
            ImageTextExtractor.Request(
                imageUrls.mapIndexed { index, url ->
                    ImageTextExtractor.ImageInput(imageIndex = index + 2, imageUrl = url)
                },
            ),
        )

        assertEquals(listOf(2, 3), transcripts.map(ImageTranscript::imageIndex))
        assertEquals(
            listOf("빈브라더스 커피하우스 서울", "서울 마포구 상수동 354-12"),
            transcripts.first().texts,
        )
        fixture.server.verify()
    }

    @Test
    fun `extracts place clues from stored image transcripts without image inputs`() {
        val fixture = adapterFixture()
        fixture.server.expect(requestTo("https://api.openai.test/v1/responses"))
            .andExpect(content().string(not(containsString("\"type\":\"input_image\""))))
            .andExpect(content().string(containsString("빈브라더스 커피하우스 서울")))
            .andExpect(content().string(containsString("층·호 정보를 그대로 유지")))
            .andExpect(content().string(containsString("\"addressHint\"")))
            .andExpect(content().string(containsString("\"max_output_tokens\":12000")))
            .andRespond(
                withSuccess(
                    response(
                        """
                        {"places":[{
                          "name":"빈브라더스 커피하우스 서울",
                          "region":"서울 마포구 상수동",
                          "addressHint":"서울 마포구 상수동 354-12 지하 1층 201호",
                          "queries":["빈브라더스 커피하우스 서울","상수동 빈브라더스"],
                          "evidence":[{"imageIndex":2,"evidenceText":"빈브라더스 커피하우스 서울"}]
                        }]}
                        """.trimIndent(),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val places = fixture.adapter.extract(
            PlaceClueExtractor.Request(
                body = null,
                hashtags = emptyList(),
                sourceLocationTag = null,
                imageTranscripts = listOf(
                    ImageTranscript(
                        2,
                        listOf("빈브라더스 커피하우스 서울", "서울 마포구 상수동 354-12 지하 1층 201호"),
                    ),
                ),
            ),
        )

        assertEquals("빈브라더스 커피하우스 서울", places.single().name)
        assertEquals("서울 마포구 상수동 354-12 지하 1층 201호", places.single().addressHint)
        assertEquals(2, places.single().evidence.single().imageIndex)
        fixture.server.verify()
    }

    @Test
    fun `selects a candidate from all search evidence`() {
        val fixture = adapterFixture()
        fixture.server.expect(requestTo("https://api.openai.test/v1/responses"))
            .andExpect(content().string(containsString("place_candidate_selection")))
            .andExpect(content().string(containsString("matchedQueries")))
            .andExpect(content().string(containsString("evidenceText")))
            .andExpect(content().string(containsString("addressHint")))
            .andExpect(content().string(containsString("서울 마포구 양화로6길 99-9 4층")))
            .andExpect(content().string(containsString("상수동 이츠야")))
            .andRespond(withSuccess(response("""{"candidateIndex":0}"""), MediaType.APPLICATION_JSON))
        val candidate = PlaceCandidate(
            provider = "KAKAO",
            externalPlaceId = "1",
            name = "이츠야",
            address = "서울 마포구 양화로6길 99-9",
            latitude = BigDecimal("37.0"),
            longitude = BigDecimal("126.0"),
            category = "음식점 > 일식 > 돈까스",
            phoneNumber = null,
            providerUrl = null,
        )

        val selected = fixture.adapter.select(
            PlaceCandidateSelector.Request(
                clue = PlaceClue(
                    name = "이츠야",
                    region = "서울특별시 서초구 상수역 인근",
                    queries = listOf("이츠야", "상수동 이츠야"),
                    evidence = listOf(PlaceClueEvidence(2, "이츠야 / 서울 마포구 양화로6길 99-9")),
                    addressHint = "서울 마포구 양화로6길 99-9 4층",
                ),
                candidates = listOf(
                    PlaceCandidateSelector.Candidate(
                        place = candidate,
                        matchedQueries = listOf("이츠야", "상수동 이츠야"),
                    ),
                ),
            ),
        )

        assertEquals(candidate, selected)
        fixture.server.verify()
    }

    @Test
    fun `returns null when no candidate has enough evidence`() {
        val fixture = adapterFixture()
        fixture.server.expect(requestTo("https://api.openai.test/v1/responses"))
            .andRespond(withSuccess(response("""{"candidateIndex":null}"""), MediaType.APPLICATION_JSON))

        val selected = fixture.adapter.select(
            PlaceCandidateSelector.Request(
                clue = PlaceClue("없는 장소", null, listOf("없는 장소")),
                candidates = listOf(
                    PlaceCandidateSelector.Candidate(
                        place = PlaceCandidate(
                            provider = "KAKAO",
                            externalPlaceId = "1",
                            name = "다른 장소",
                            address = "서울 마포구",
                            latitude = BigDecimal("37.0"),
                            longitude = BigDecimal("126.0"),
                            category = null,
                            phoneNumber = null,
                            providerUrl = null,
                        ),
                        matchedQueries = listOf("없는 장소"),
                    ),
                ),
            ),
        )

        assertEquals(null, selected)
        fixture.server.verify()
    }

    @Test
    fun `uses Instagram location tag only as a regional search hint`() {
        val fixture = adapterFixture()
        fixture.server.expect(requestTo("https://api.openai.test/v1/responses"))
            .andExpect(content().string(containsString("지역 문맥과 검색어를 보조하는 힌트")))
            .andExpect(content().string(containsString("장소 존재의 근거로 사용하지 않는다")))
            .andExpect(content().string(containsString("\\\"sourceLocationTag\\\":\\\"Lodge190\\\"")))
            .andExpect(content().string(containsString("\"maxItems\":4")))
            .andRespond(
                withSuccess(
                    response(
                        """
                        {
                          "places": [{
                            "name": "Lodge190",
                            "region": "연희동",
                            "queries": ["Lodge190", "롯지190", "롯지 190", "연희동 Lodge"],
                            "evidence": []
                          }]
                        }
                        """.trimIndent(),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val places = fixture.adapter.extract(
            PlaceClueExtractor.Request(
                body = "연희동 사랑방 롯지190 매년 여름이 오면 이 집 빙수를 먹으러 와야 합니다",
                hashtags = emptyList(),
                sourceLocationTag = "Lodge190",
            ),
        )

        assertEquals("Lodge190", places.single().name)
        assertEquals("연희동", places.single().region)
        assertEquals(
            listOf("Lodge190", "롯지190", "롯지 190", "연희동 Lodge"),
            places.single().queries,
        )
        fixture.server.verify()
    }

    @Test
    fun `fails when OpenAI refuses the request`() {
        val fixture = adapterFixture()
        fixture.server.expect(requestTo("https://api.openai.test/v1/responses"))
            .andRespond(withSuccess(REFUSAL_RESPONSE, MediaType.APPLICATION_JSON))

        assertFailsWith<IllegalStateException> {
            fixture.adapter.infer(PostContentInference.Request(null, emptyList(), null))
        }
    }

    private fun adapterFixture(): AdapterFixture {
        val builder = RestClient.builder().baseUrl("https://api.openai.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val restClient = builder.build()
        val objectMapper = jacksonObjectMapper()
        val properties = OpenAiProperties(apiKey = "test-key")
        return AdapterFixture(
            adapter = OpenAiContentInferenceAdapter(
                restClient = restClient,
                objectMapper = objectMapper,
                properties = properties,
            ),
            coverTitleExtractor = OpenAiCoverTitleExtractor(restClient, objectMapper, properties),
            imageTextExtractor = OpenAiImageTextExtractor(restClient, objectMapper, properties),
            server = server,
        )
    }

    private fun candidate(): PlaceCandidate = PlaceCandidate(
        provider = "KAKAO",
        externalPlaceId = "1",
        name = "누크 식당",
        address = "서울",
        latitude = BigDecimal("37.0"),
        longitude = BigDecimal("127.0"),
        category = "음식점",
        phoneNumber = null,
        providerUrl = null,
    )

    private data class AdapterFixture(
        val adapter: OpenAiContentInferenceAdapter,
        val coverTitleExtractor: OpenAiCoverTitleExtractor,
        val imageTextExtractor: OpenAiImageTextExtractor,
        val server: MockRestServiceServer,
    )

    private companion object {
        fun response(output: String): String =
            """
            {
              "output": [{
                "type": "message",
                "content": [{"type":"output_text","text":${jacksonObjectMapper().writeValueAsString(output)}}]
              }]
            }
            """.trimIndent()

        val REFUSAL_RESPONSE =
            """
            {
              "output": [{
                "type": "message",
                "content": [{"type":"refusal","refusal":"cannot comply"}]
              }]
            }
            """.trimIndent()

        val TEST_IMAGE_URLS = listOf(
            "https://d6idqwsn9nndw.cloudfront.net/post-media/sha256/92/" +
                "9289d25c46d072bb44c029ac4bc1c50db9309cf587306698027e2cbb7030d091.jpg",
            "https://d6idqwsn9nndw.cloudfront.net/post-media/sha256/ed/" +
                "ede59b899f821acced2fd262fe917790101c912cc28004759d851c55486d25bb.jpg",
            "https://d6idqwsn9nndw.cloudfront.net/post-media/sha256/fb/" +
                "fb5d58281d59e33733466809aacf1ef2e1e64546341749c32058fad899050a58.jpg",
            "https://d6idqwsn9nndw.cloudfront.net/post-media/sha256/23/" +
                "236b82c2fc5bc8d1b74f916b3723602cbb9885378bef78e7ff3c5d553372cca2.jpg",
            "https://d6idqwsn9nndw.cloudfront.net/post-media/sha256/94/" +
                "94dc4c636d73497f15f84c5308863583713a9c2cab459fa69f44408b965f76d0.jpg",
            "https://d6idqwsn9nndw.cloudfront.net/post-media/sha256/8c/" +
                "8cdb4fb329f245a65ad5f7de7298ea12ec1c3bfea6954824989a9c09fbd1d8b5.jpg",
            "https://d6idqwsn9nndw.cloudfront.net/post-media/sha256/1c/" +
                "1c8a8308f270eeb448ebc83a618268e2345251a162ad25fa29b191d35385483c.jpg",
        )
    }
}
