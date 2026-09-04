package org.every.nook.api.application.place

import org.every.nook.api.application.processing.ParsingPolicyEvaluation

internal class ManualPlaceCandidateFallback(private val searchPort: ManualPlaceCandidateSearchPort) {
    fun evaluate(
        clue: PlaceClue,
        externalCandidates: List<PlaceCandidateSelector.Candidate>,
        policy: CandidateResolutionPolicy,
    ): Result {
        val externalEvaluation = policy.evaluate(CandidateResolutionPolicy.Context(clue, externalCandidates))
        if (externalEvaluation.result.compatibleCandidates.isNotEmpty()) {
            return Result(externalCandidates, externalEvaluation, null)
        }
        val manualCandidates = searchPort.findByName(clue.name.trim()).map { candidate ->
            PlaceCandidateSelector.Candidate(
                place = candidate,
                matchedQueries = listOf(clue.name.trim()),
                matchedQueryRanks = mapOf(clue.name.trim() to 0),
                supportingProviders = setOf(candidate.provider),
            )
        }
        val evaluation = manualCandidates.takeIf(List<*>::isNotEmpty)
            ?.let { policy.evaluate(CandidateResolutionPolicy.Context(clue, manualCandidates)) }
            ?: externalEvaluation
        return Result(manualCandidates.ifEmpty { externalCandidates }, evaluation, manualCandidates.size)
    }

    data class Result(
        val candidates: List<PlaceCandidateSelector.Candidate>,
        val evaluation: ParsingPolicyEvaluation<CandidateResolutionPolicy.AutomaticResult>,
        val manualCandidateCount: Int?,
    )
}
