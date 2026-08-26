package org.every.nook.api.infrastructure.openai

import org.every.nook.api.application.content.SourceProfileHint
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
import org.every.nook.api.application.post.PostTitleSelector
import org.every.nook.api.application.providerusage.OpenAiTokenUsage
import org.every.nook.api.application.providerusage.OpenAiTokenUsageRecorder
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
        assertEquals("place_tags", fixture.recordedUsage.single().feature)
        assertEquals("gpt-test-2026-08-27", fixture.recordedUsage.single().model)
        assertEquals(150, fixture.recordedUsage.single().totalTokens)
        fixture.server.verify()
    }

    @Test
    fun `extracts text place clues without generating an early title`() {
        val fixture = adapterFixture()
        fixture.server.expect(requestTo("https://api.openai.test/v1/responses"))
            .andExpect(header("Authorization", "Bearer test-key"))
            .andExpect(content().string(containsString("post_content_inference")))
            .andExpect(content().string(containsString("\"model\":\"gpt-5-mini\"")))
            .andExpect(content().string(not(containsString("\"title\""))))
            .andExpect(content().string(containsString("\"maxItems\":60")))
            .andRespond(
                withSuccess(
                    response(
                        """
                        {
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

        assertEquals(listOf("원동미나리삼겹살"), inference.placeClues.map(PlaceClue::name))
        fixture.server.verify()
    }

    @Test
    fun `selects a final title from body OCR and resolved places`() {
        val fixture = adapterFixture()
        fixture.server.expect(requestTo("https://api.openai.test/v1/responses"))
            .andExpect(content().string(containsString("post_title_selection")))
            .andExpect(content().string(containsString("카페 in 홍대")))
            .andExpect(content().string(containsString("아프리포코")))
            .andExpect(content().string(containsString("resolvedPlaces")))
            .andExpect(content().string(containsString("PICK, VOL")))
            .andRespond(
                withSuccess(
                    response(
                        """
                        {
                          "title":"홍대 살구 디저트 카페 아프리포코",
                          "source":"COMBINED",
                          "evidence":["살구로 물든 여름","아프리포코"],
                          "rejectedCoverReason":"장식 문구만으로 주제를 설명하지 못함"
                        }
                        """.trimIndent(),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = fixture.titleSelector.select(
            PostTitleSelector.Request(
                body = "살구로 물든 여름. 아프리포코의 살구 디저트를 소개합니다.",
                hashtags = listOf("홍대카페", "살구디저트"),
                sourceLocationTag = "합정&홍대",
                coverTexts = listOf("카페 in 홍대", "ApriPoco", "살구 케이크"),
                declaredPlaceCount = null,
                places = listOf(
                    PostTitleSelector.Place(
                        name = "아프리포코",
                        address = "서울 마포구 동교로 142-16",
                        city = "서울특별시",
                        category = "음식점 > 카페",
                    ),
                ),
            ),
        )

        assertEquals("홍대 살구 디저트 카페 아프리포코", result.title)
        assertEquals(PostTitleSelector.Source.COMBINED, result.source)
        assertEquals("장식 문구만으로 주제를 설명하지 못함", result.rejectedCoverReason)
        fixture.server.verify()
    }

    @Test
    fun `selects an exact cover title from OCR texts`() {
        val fixture = adapterFixture()
        fixture.server.expect(requestTo("https://api.openai.test/v1/responses"))
            .andExpect(content().string(containsString("요즘 뜨고 있는 금주의 신상스폿")))
            .andExpect(content().string(containsString("post_cover_title")))
            .andExpect(content().string(containsString("계정명, 로고")))
            .andRespond(
                withSuccess(
                    response(
                        """
                        {"title":"요즘 뜨고 있는 금주의 신상스폿"}
                        """.trimIndent(),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val title = fixture.coverTitleExtractor.extract(
            CoverTitleExtractor.Request(listOf("6월 2주차", "요즘 뜨고 있는 금주의 신상스폿")),
        )

        assertEquals("요즘 뜨고 있는 금주의 신상스폿", title)
        fixture.server.verify()
    }

    @Test
    fun `extracts multiple grounded place clues`() {
        val fixture = adapterFixture()
        fixture.server.expect(requestTo("https://api.openai.test/v1/responses"))
            .andExpect(content().string(containsString("place_clues")))
            .andExpect(content().string(containsString("\"model\":\"gpt-5-mini\"")))
            .andExpect(content().string(containsString("반복되는 매거진명, 계정명, 로고, 워터마크")))
            .andExpect(content().string(containsString("3 초라멘 | 화양동")))
            .andExpect(content().string(containsString("상위 행정구나 대표 인근역")))
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
    fun `includes body-mentioned profile hints in text place extraction`() {
        val fixture = adapterFixture()
        fixture.server.expect(requestTo("https://api.openai.test/v1/responses"))
            .andExpect(content().string(containsString("sourceProfileHints")))
            .andExpect(content().string(containsString("stuff_itaewon")))
            .andExpect(content().string(containsString("STUFF")))
            .andExpect(content().string(containsString("본문에 실제로 언급된 계정")))
            .andRespond(
                withSuccess(
                    response(
                        """{"places":[{"name":"STUFF","region":"이태원","addressHint":null,""" +
                            """"queries":["이태원 STUFF"],"evidence":[]}]}""",
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val places = fixture.adapter.extract(
            PlaceClueExtractor.Request(
                body = "이태원 빈티지 숍 @stuff_itaewon",
                hashtags = emptyList(),
                sourceLocationTag = "이태원",
                sourceProfileHints = listOf(SourceProfileHint("STUFF😊", "stuff_itaewon")),
            ),
        )

        assertEquals("STUFF", places.single().name)
        fixture.server.verify()
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
            .andExpect(content().string(containsString("뒤쪽의 별도 문단")))
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
        val recordedUsage = mutableListOf<OpenAiTokenUsage>()
        val tokenUsageTracker = OpenAiTokenUsageTracker(OpenAiTokenUsageRecorder(recordedUsage::add))
        return AdapterFixture(
            adapter = OpenAiContentInferenceAdapter(
                restClient = restClient,
                objectMapper = objectMapper,
                properties = properties,
                tokenUsageTracker = tokenUsageTracker,
            ),
            titleSelector = OpenAiPostTitleSelector(restClient, objectMapper, properties, tokenUsageTracker),
            coverTitleExtractor = OpenAiCoverTitleExtractor(restClient, objectMapper, properties, tokenUsageTracker),
            imageTextExtractor = OpenAiImageTextExtractor(restClient, objectMapper, properties, tokenUsageTracker),
            server = server,
            recordedUsage = recordedUsage,
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
        val titleSelector: OpenAiPostTitleSelector,
        val coverTitleExtractor: OpenAiCoverTitleExtractor,
        val imageTextExtractor: OpenAiImageTextExtractor,
        val server: MockRestServiceServer,
        val recordedUsage: List<OpenAiTokenUsage>,
    )

    private companion object {
        fun response(output: String): String =
            """
            {
              "model": "gpt-test-2026-08-27",
              "usage": {
                "input_tokens": 120,
                "input_tokens_details": {"cached_tokens": 40},
                "output_tokens": 30,
                "total_tokens": 150
              },
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
