package org.every.nook.api.infrastructure.place

import mu.KotlinLogging
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSearchProvider

class PrioritizedPlaceSearchProvider(private val kakao: PlaceSearchProvider, private val naver: PlaceSearchProvider) :
    PlaceSearchProvider {
    override fun search(request: PlaceSearchProvider.Request): List<PlaceCandidate> {
        val queryContext = QueryContext.from(request.query)
        val naverCandidates = runCatching { naver.search(request) }.getOrElse { exception ->
            logger.debug(exception) { "[PostParcingTracker] stage=NAVER_SEARCH status=FAILED query=${request.query}" }
            emptyList()
        }.sortedByDescending { score(queryContext, it) }
        val bestNaverScore = naverCandidates.firstOrNull()?.let { score(queryContext, it) } ?: 0
        logger.debug {
            "[PostParcingTracker] stage=NAVER_SEARCH status=COMPLETED query=${request.query} " +
                "candidateCount=${naverCandidates.size} bestScore=$bestNaverScore"
        }
        if (bestNaverScore >= NAVER_CONFIDENCE_SCORE) {
            logger.debug { "[PostParcingTracker] stage=KAKAO_SEARCH status=SKIPPED reason=naver_confident" }
            return naverCandidates
        }

        val kakaoCandidates = runCatching { kakao.search(request) }.getOrElse { exception ->
            logger.debug(exception) { "[PostParcingTracker] stage=KAKAO_SEARCH status=FAILED query=${request.query}" }
            emptyList()
        }
        logger.debug {
            "[PostParcingTracker] stage=KAKAO_SEARCH status=COMPLETED query=${request.query} " +
                "candidateCount=${kakaoCandidates.size}"
        }
        if (naverCandidates.isEmpty()) {
            return kakaoCandidates.sortedByDescending { score(queryContext, it) }
        }
        val validatedNaverCandidates = naverCandidates.sortedByDescending { candidate ->
            score(queryContext, candidate) +
                kakaoValidationScore(
                    candidate,
                    kakaoCandidates,
                    queryContext,
                )
        }
        val rankedKakaoCandidates = kakaoCandidates.sortedByDescending { score(queryContext, it) }
        return (validatedNaverCandidates + rankedKakaoCandidates)
            .distinctBy { candidate -> candidate.provider to candidate.externalPlaceId }
    }

    private fun kakaoValidationScore(
        candidate: PlaceCandidate,
        kakaoCandidates: List<PlaceCandidate>,
        queryContext: QueryContext,
    ): Int {
        @Suppress("FunctionExpressionBody")
        return if (kakaoCandidates.any { kakao ->
                val namesMatch = candidate.name.normalize().let { naverName ->
                    val kakaoName = kakao.name.normalize()
                    naverName.contains(kakaoName) || kakaoName.contains(naverName)
                }
                val addressesMatch = candidate.address.tokens().intersect(kakao.address.tokens().toSet()).size >=
                    MIN_SHARED_ADDRESS_TOKEN_COUNT
                val regionsAlign = regionScore(queryContext, candidate.address) > 0 &&
                    regionScore(queryContext, kakao.address) > 0
                namesMatch && addressesMatch && regionsAlign
            }
        ) {
            KAKAO_VALIDATION_SCORE
        } else {
            0
        }
    }

    private fun score(queryContext: QueryContext, candidate: PlaceCandidate): Int {
        val normalizedQuery = queryContext.normalizedQuery
        val normalizedName = candidate.name.normalize()
        val normalizedAddress = candidate.address.normalize()
        val baseScore = when {
            normalizedName == normalizedQuery -> EXACT_NAME_SCORE
            normalizedName.contains(normalizedQuery) || normalizedQuery.contains(normalizedName) -> CONTAINS_NAME_SCORE
            queryContext.tokens.any { it.length >= MIN_TOKEN_LENGTH && normalizedName.contains(it) } -> TOKEN_NAME_SCORE
            queryContext.tokens.any { it.length >= MIN_TOKEN_LENGTH && normalizedAddress.contains(it) } -> ADDRESS_SCORE
            else -> 0
        }
        return baseScore +
            regionScore(queryContext, candidate.address) -
            regionMismatchPenalty(queryContext, candidate.address)
    }

    private fun regionScore(queryContext: QueryContext, address: String): Int {
        if (queryContext.regionTokens.isEmpty()) return 0
        val sharedCount = queryContext.regionTokens.intersect(address.tokens().toSet()).size
        return when {
            sharedCount >= 2 -> STRONG_REGION_SCORE
            sharedCount == 1 -> WEAK_REGION_SCORE
            else -> 0
        }
    }

    private fun regionMismatchPenalty(queryContext: QueryContext, address: String): Int {
        if (queryContext.regionTokens.isEmpty()) return 0
        return if (queryContext.regionTokens.intersect(address.tokens().toSet()).isEmpty()) {
            REGION_MISMATCH_PENALTY
        } else {
            0
        }
    }

    private fun String.normalize(): String = lowercase().filter(Char::isLetterOrDigit)
    private fun String.tokens(): List<String> = lowercase().split(Regex("[^가-힣a-z0-9]+"))

    private data class QueryContext(
        val normalizedQuery: String,
        val tokens: List<String>,
        val regionTokens: Set<String>,
    ) {
        companion object {
            fun from(query: String): QueryContext {
                val tokens = tokenize(query).filter { it.length >= MIN_TOKEN_LENGTH }
                val regionTokens = tokens.filter(::isRegionToken).toSet()
                return QueryContext(
                    normalizedQuery = normalize(query),
                    tokens = tokens,
                    regionTokens = regionTokens,
                )
            }

            private fun isRegionToken(token: String): Boolean = REGION_SUFFIXES.any(token::endsWith)
            private fun normalize(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)
            private fun tokenize(value: String): List<String> = value.lowercase().split(Regex("[^가-힣a-z0-9]+"))
        }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
        const val NAVER_CONFIDENCE_SCORE = 80
        const val EXACT_NAME_SCORE = 100
        const val CONTAINS_NAME_SCORE = 80
        const val TOKEN_NAME_SCORE = 55
        const val ADDRESS_SCORE = 30
        const val MIN_TOKEN_LENGTH = 2
        const val MIN_SHARED_ADDRESS_TOKEN_COUNT = 2
        const val KAKAO_VALIDATION_SCORE = 30
        const val STRONG_REGION_SCORE = 35
        const val WEAK_REGION_SCORE = 15
        const val REGION_MISMATCH_PENALTY = 40
        val REGION_SUFFIXES = listOf("시", "도", "군", "구", "동", "로", "길")
    }
}
