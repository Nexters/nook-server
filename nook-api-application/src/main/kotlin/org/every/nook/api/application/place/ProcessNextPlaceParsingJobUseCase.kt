package org.every.nook.api.application.place

class ProcessNextPlaceParsingJobUseCase(
    private val jobPort: PlaceParsingJobPort,
    private val clueExtractor: PlaceClueExtractor,
    private val searchPlaceCandidates: SearchPlaceCandidatesUseCase,
) {
    operator fun invoke(): Boolean {
        val job = jobPort.claimNext() ?: return false

        runCatching {
            val clues = clueExtractor.extract(
                PlaceClueExtractor.Request(
                    body = job.body,
                    hashtags = job.hashtags,
                    sourceLocationTag = job.sourceLocationTag,
                ),
            )
            require(clues.size <= MAX_PLACE_COUNT) { "Too many place clues" }
            val places = clues.map(::resolve)
            jobPort.complete(job.postId, places)
        }.onFailure { exception ->
            jobPort.fail(
                postId = job.postId,
                reason = exception.message.orEmpty().ifBlank { DEFAULT_FAILURE_REASON }.take(MAX_FAILURE_REASON_LENGTH),
            )
        }

        return true
    }

    private fun resolve(clue: PlaceClue): PlaceCandidate {
        require(clue.name.isNotBlank() && clue.queries.isNotEmpty() && clue.queries.size <= MAX_QUERY_COUNT) {
            "Invalid place clue"
        }
        val candidates = searchPlaceCandidates(
            SearchPlaceCandidatesUseCase.Command(queries = clue.queries),
        )
        val normalizedName = clue.name.normalize()
        val normalizedRegion = clue.region?.normalize()?.takeIf(String::isNotEmpty)
        val matches = candidates.filter { candidate ->
            candidate.name.normalize() == normalizedName &&
                (normalizedRegion == null || candidate.address.normalize().contains(normalizedRegion))
        }

        return matches.singleOrNull()
            ?: error("Place could not be uniquely identified: ${clue.name}")
    }

    private fun String.normalize(): String = lowercase().filterNot(Char::isWhitespace)

    private companion object {
        const val MAX_PLACE_COUNT = 10
        const val MAX_QUERY_COUNT = 3
        const val MAX_FAILURE_REASON_LENGTH = 500
        const val DEFAULT_FAILURE_REASON = "Place parsing failed"
    }
}
