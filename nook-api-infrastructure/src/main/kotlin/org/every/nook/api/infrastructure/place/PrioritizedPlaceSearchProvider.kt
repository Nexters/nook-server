package org.every.nook.api.infrastructure.place

import mu.KotlinLogging
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSearchProvider

class PrioritizedPlaceSearchProvider(private val kakao: PlaceSearchProvider, private val naver: PlaceSearchProvider) :
    PlaceSearchProvider {
    override fun search(request: PlaceSearchProvider.Request): List<PlaceCandidate> {
        val kakaoCandidates = runCatching { kakao.search(request) }.getOrElse { exception ->
            logger.debug(exception) { "[PostParcingTracker] stage=KAKAO_SEARCH status=FAILED query=${request.query}" }
            emptyList()
        }.sortedByDescending { score(request.query, it) }
        val bestKakaoScore = kakaoCandidates.firstOrNull()?.let { score(request.query, it) } ?: 0
        logger.debug {
            "[PostParcingTracker] stage=KAKAO_SEARCH status=COMPLETED query=${request.query} " +
                "candidateCount=${kakaoCandidates.size} bestScore=$bestKakaoScore"
        }
        if (bestKakaoScore >= KAKAO_CONFIDENCE_SCORE) {
            logger.debug { "[PostParcingTracker] stage=NAVER_SEARCH status=SKIPPED reason=kakao_confident" }
            return kakaoCandidates
        }

        val naverCandidates = runCatching { naver.search(request) }.getOrElse { exception ->
            logger.debug(exception) { "[PostParcingTracker] stage=NAVER_SEARCH status=FAILED query=${request.query}" }
            emptyList()
        }
        logger.debug {
            "[PostParcingTracker] stage=NAVER_SEARCH status=COMPLETED query=${request.query} " +
                "candidateCount=${naverCandidates.size}"
        }
        if (kakaoCandidates.isEmpty()) {
            return naverCandidates.sortedByDescending { score(request.query, it) }
        }
        return kakaoCandidates.sortedByDescending { candidate ->
            score(request.query, candidate) + naverValidationScore(candidate, naverCandidates)
        }
    }

    private fun naverValidationScore(candidate: PlaceCandidate, naverCandidates: List<PlaceCandidate>): Int =
        if (naverCandidates.any { naver ->
                val namesMatch = candidate.name.normalize().let { kakaoName ->
                    val naverName = naver.name.normalize()
                    kakaoName.contains(naverName) || naverName.contains(kakaoName)
                }
                val addressesMatch = candidate.address.tokens().intersect(naver.address.tokens().toSet()).size >=
                    MIN_SHARED_ADDRESS_TOKEN_COUNT
                namesMatch && addressesMatch
            }
        ) {
            NAVER_VALIDATION_SCORE
        } else {
            0
        }

    private fun score(query: String, candidate: PlaceCandidate): Int {
        val normalizedQuery = query.normalize()
        val normalizedName = candidate.name.normalize()
        val normalizedAddress = candidate.address.normalize()
        return when {
            normalizedName == normalizedQuery -> EXACT_NAME_SCORE
            normalizedName.contains(normalizedQuery) || normalizedQuery.contains(normalizedName) -> CONTAINS_NAME_SCORE
            query.tokens().any { it.length >= MIN_TOKEN_LENGTH && normalizedName.contains(it) } -> TOKEN_NAME_SCORE
            query.tokens().any { it.length >= MIN_TOKEN_LENGTH && normalizedAddress.contains(it) } -> ADDRESS_SCORE
            else -> 0
        }
    }

    private fun String.normalize(): String = lowercase().filter(Char::isLetterOrDigit)
    private fun String.tokens(): List<String> = lowercase().split(Regex("[^가-힣a-z0-9]+"))

    private companion object {
        val logger = KotlinLogging.logger {}
        const val KAKAO_CONFIDENCE_SCORE = 80
        const val EXACT_NAME_SCORE = 100
        const val CONTAINS_NAME_SCORE = 80
        const val TOKEN_NAME_SCORE = 55
        const val ADDRESS_SCORE = 30
        const val MIN_TOKEN_LENGTH = 2
        const val MIN_SHARED_ADDRESS_TOKEN_COUNT = 2
        const val NAVER_VALIDATION_SCORE = 30
    }
}
