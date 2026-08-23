package org.every.nook.api.application.place

import org.every.nook.api.application.processing.ParsingFact
import org.every.nook.api.application.processing.ParsingFactValue
import org.every.nook.api.application.processing.ParsingFieldDefinition
import org.every.nook.api.application.processing.ParsingPolicy
import org.every.nook.api.application.processing.ParsingPolicyEvaluation
import org.every.nook.api.application.processing.ParsingRule
import org.every.nook.api.application.processing.ParsingRuleDecision
import org.every.nook.api.application.processing.ParsingRuleDefinition
import org.every.nook.api.application.processing.ParsingRuleEffect
import org.every.nook.api.application.processing.ParsingRuleEvaluation
import org.every.nook.api.application.processing.ParsingRuleId
import org.every.nook.api.application.processing.ParsingRuleOutcome
import org.every.nook.api.application.processing.ParsingStepDefinition
import org.every.nook.api.application.processing.ParsingStepId

internal class CandidateResolutionPolicy :
    ParsingPolicy<CandidateResolutionPolicy.Context, CandidateResolutionPolicy.AutomaticResult>(STEP) {
    @Suppress("ReturnCount") // Each return is a terminal rule outcome in the ordered policy.
    override fun evaluatePolicy(context: Context): ParsingPolicyEvaluation<AutomaticResult> {
        val compatibleCandidates = context.candidates.compatibleWith(context.clue).distinctLogicalCandidates()
        val effectiveContext = context.copy(candidates = compatibleCandidates)
        val strictMatches = compatibleCandidates.strictMatches(context.clue)
        val groundedCandidates = compatibleCandidates.groundedCandidateMatches(context.clue)
        val groundedMatches = groundedCandidates.matches
        val facts = MatchFacts(effectiveContext, strictMatches, groundedMatches)
        val evaluations = mutableListOf<ParsingRuleEvaluation>()
        val compatible = COMPATIBLE_CANDIDATE_RULE.evaluate(facts).also(evaluations::add)
        if (compatible.outcome == ParsingRuleOutcome.FAILED) {
            evaluations += remainingAutomaticRules().map { it.skipped("호환 후보 없음") }
            return result(null, compatibleCandidates, strictMatches, groundedCandidates, evaluations)
        }
        val strict = UNIQUE_STRICT_RULE.evaluate(facts).also(evaluations::add)
        if (strict.outcome == ParsingRuleOutcome.PASSED) {
            evaluations += listOf(UNIQUE_GROUNDED_RULE, SEARCH_EVIDENCE_RULE)
                .map { it.skipped("strict 후보 확정") }
            return result(
                CandidateSelection(strictMatches.single().place, "strict_match"),
                compatibleCandidates,
                strictMatches,
                groundedCandidates,
                evaluations,
            )
        }
        val grounded = UNIQUE_GROUNDED_RULE.evaluate(facts).also(evaluations::add)
        if (grounded.outcome == ParsingRuleOutcome.PASSED) {
            evaluations += SEARCH_EVIDENCE_RULE.skipped("grounded 후보 확정")
            return result(
                CandidateSelection(groundedMatches.single().place, "grounded_match"),
                compatibleCandidates,
                strictMatches,
                groundedCandidates,
                evaluations,
            )
        }
        val searchSelection = searchEvidenceSelection(context.clue, compatibleCandidates)
        evaluations += SEARCH_EVIDENCE_RULE.evaluate(facts.copy(searchEvidenceSelection = searchSelection))
        return result(searchSelection, compatibleCandidates, strictMatches, groundedCandidates, evaluations)
    }

    fun evaluateModelSelection(selected: PlaceCandidate?): ParsingRuleEvaluation =
        MODEL_SELECTION_RULE.evaluate(selected)

    fun evaluateSelectionValidation(
        clue: PlaceClue,
        selection: CandidateSelection,
        candidates: List<PlaceCandidateSelector.Candidate>,
        explicitNameSearchMatch: PlaceCandidateSelector.Candidate?,
    ): ParsingRuleEvaluation {
        val matchedQueries = candidates.matchedQueriesFor(selection.place)
        return SELECTION_VALIDATION_RULE.evaluate(
            ValidationContext(
                selection,
                matchedQueries,
                clue.isSupportedBy(selection.place, matchedQueries) || explicitNameSearchMatch.matches(selection.place),
            ),
        )
    }

    data class Context(val clue: PlaceClue, val candidates: List<PlaceCandidateSelector.Candidate>)

    data class AutomaticResult(
        val selection: CandidateSelection?,
        val compatibleCandidates: List<PlaceCandidateSelector.Candidate>,
        val strictMatches: List<PlaceCandidateSelector.Candidate>,
        val groundedMatches: List<PlaceCandidateSelector.Candidate>,
        val explicitNameSearchMatch: PlaceCandidateSelector.Candidate?,
    )

    private data class MatchFacts(
        val context: Context,
        val strictMatches: List<PlaceCandidateSelector.Candidate>,
        val groundedMatches: List<PlaceCandidateSelector.Candidate>,
        val searchEvidenceSelection: CandidateSelection? = null,
    )

    private data class ValidationContext(
        val selection: CandidateSelection,
        val matchedQueries: List<String>,
        val grounded: Boolean,
    )

    companion object {
        const val MIN_TOP_CONTEXTUAL_QUERY_SUPPORT = 2
        const val MIN_PROVIDER_SUPPORT = 2

        val STEP by lazy {
            ParsingStepDefinition(
                id = ParsingStepId("text-resolution"),
                title = "장소 후보 판정",
                description = "검색 결과를 주소와 이름 근거로 좁히고 안전한 순서로 장소를 확정합니다.",
                inputs = listOf(
                    ParsingFieldDefinition("place-clue", "장소 단서"),
                    ParsingFieldDefinition("place-candidates", "지도 검색 후보"),
                ),
                outputs = listOf(
                    ParsingFieldDefinition("selected-place", "확정 장소"),
                    ParsingFieldDefinition("unresolved-place-clue", "미해결 장소 단서"),
                ),
                ruleIds = RULES.map(ParsingRuleDefinition::id),
            )
        }

        val RULES by lazy {
            listOf(
                COMPATIBLE_CANDIDATE_DEFINITION,
                UNIQUE_STRICT_DEFINITION,
                UNIQUE_GROUNDED_DEFINITION,
                SEARCH_EVIDENCE_DEFINITION,
                MODEL_SELECTION_DEFINITION,
                SELECTION_VALIDATION_DEFINITION,
            )
        }

        private val COMPATIBLE_CANDIDATE_DEFINITION = definition(
            "place.candidate.compatible",
            "검색 후보 호환성",
            "주소와 명시된 층·호수가 충돌하지 않는 후보가 있는가?",
            "compatibleCandidates.isNotEmpty()",
            "후보 판정 계속",
            "장소 후보 없음으로 처리",
        )
        private val UNIQUE_STRICT_DEFINITION = definition(
            "place.candidate.unique-strict",
            "Strict 후보 확정",
            "상호명, 지역, 주소가 엄격하게 일치하는 후보가 정확히 하나인가?",
            "strictMatches.size == 1",
            "해당 장소 즉시 확정",
            "grounded 후보 확인",
        )
        private val UNIQUE_GROUNDED_DEFINITION = definition(
            "place.candidate.unique-grounded",
            "Grounded 후보 확정",
            "원문·이미지·주소 검색 근거가 있는 후보가 정확히 하나인가?",
            "groundedMatches.size == 1",
            "해당 장소 즉시 확정",
            "검색 근거 기반 복구 확인",
        )
        private val SEARCH_EVIDENCE_DEFINITION = ParsingRuleDefinition(
            id = ParsingRuleId("place.candidate.search-evidence"),
            title = "검색 근거 기반 복구",
            description = "복수 맥락 검색어와 복수 provider가 같은 장소를 지지하고 동명 지점이 없을 때 복구합니다.",
            condition = "검색어·provider 지지가 임계값 이상이고 동명 후보가 하나인가?",
            expression = "topQueryCount >= minimumTopQueries && providers >= minimumProviders && sameNameCount == 1",
            source = "CandidateResolutionPolicy",
            parameters = mapOf(
                "minimum-top-contextual-query-support" to
                    ParsingFactValue.Count(MIN_TOP_CONTEXTUAL_QUERY_SUPPORT),
                "minimum-provider-support" to ParsingFactValue.Count(MIN_PROVIDER_SUPPORT),
            ),
            onPassed = ParsingRuleEffect("검색 근거 장소 확정"),
            onFailed = ParsingRuleEffect("모델 선택 요청"),
        )
        private val MODEL_SELECTION_DEFINITION = definition(
            "place.candidate.model-selection",
            "모델 후보 선택",
            "모델이 검색 후보 중 하나를 명확하게 선택했는가?",
            "selectedCandidate != null",
            "선택 근거 재검증",
            "미해결 단서로 기록",
        )
        private val SELECTION_VALIDATION_DEFINITION = definition(
            "place.candidate.selection-validation",
            "선택 후보 최종 검증",
            "선택 후보가 원문·이미지·주소·검색 근거를 만족하는가?",
            "clue.isSupportedBy(selected, matchedQueries)",
            "최종 장소로 확정",
            "미해결 단서로 기록",
        )

        private val COMPATIBLE_CANDIDATE_RULE = booleanMatchRule(COMPATIBLE_CANDIDATE_DEFINITION) {
            it.context.candidates.isNotEmpty()
        }
        private val UNIQUE_STRICT_RULE = booleanMatchRule(UNIQUE_STRICT_DEFINITION) { it.strictMatches.size == 1 }
        private val UNIQUE_GROUNDED_RULE = booleanMatchRule(UNIQUE_GROUNDED_DEFINITION) {
            it.groundedMatches.size == 1
        }
        private val SEARCH_EVIDENCE_RULE = booleanMatchRule(SEARCH_EVIDENCE_DEFINITION) {
            it.searchEvidenceSelection != null
        }
        private val MODEL_SELECTION_RULE = ParsingRule<PlaceCandidate?>(MODEL_SELECTION_DEFINITION) { selected ->
            ParsingRuleDecision(
                outcome = selected.toOutcome(),
                facts = listOf(
                    ParsingFact("selected", "후보 선택", ParsingFactValue.Flag(selected != null)),
                    ParsingFact("selected-name", "선택 장소", ParsingFactValue.Text(selected?.name.orEmpty())),
                ),
                reason = if (selected != null) "모델 후보 선택 완료" else "모델이 후보를 선택하지 않음",
            )
        }
        private val SELECTION_VALIDATION_RULE = ParsingRule<ValidationContext>(SELECTION_VALIDATION_DEFINITION) {
            ParsingRuleDecision(
                outcome = it.grounded.toOutcome(),
                facts = listOf(
                    ParsingFact("selected-name", "선택 장소", ParsingFactValue.Text(it.selection.place.name)),
                    ParsingFact("matched-queries", "일치 검색어", ParsingFactValue.TextList(it.matchedQueries)),
                    ParsingFact("grounded", "근거 확인", ParsingFactValue.Flag(it.grounded)),
                ),
                reason = if (it.grounded) "선택 후보 근거 확인" else "선택 후보 근거 부족",
            )
        }

        private fun definition(
            id: String,
            title: String,
            condition: String,
            expression: String,
            onPassed: String,
            onFailed: String,
        ) = ParsingRuleDefinition(
            ParsingRuleId(id),
            title,
            condition,
            condition,
            expression,
            "CandidateResolutionPolicy",
            onPassed = ParsingRuleEffect(onPassed),
            onFailed = ParsingRuleEffect(onFailed),
        )

        private fun booleanMatchRule(
            definition: ParsingRuleDefinition,
            predicate: (MatchFacts) -> Boolean,
        ): ParsingRule<MatchFacts> = ParsingRule(definition) { facts ->
            val matched = predicate(facts)
            ParsingRuleDecision(
                outcome = matched.toOutcome(),
                facts = listOf(
                    ParsingFact(
                        "compatible-candidate-count",
                        "호환 후보 수",
                        ParsingFactValue.Count(facts.context.candidates.size),
                    ),
                    ParsingFact("strict-match-count", "Strict 후보 수", ParsingFactValue.Count(facts.strictMatches.size)),
                    ParsingFact(
                        "grounded-match-count",
                        "Grounded 후보 수",
                        ParsingFactValue.Count(facts.groundedMatches.size),
                    ),
                    ParsingFact("condition-matched", "조건 충족", ParsingFactValue.Flag(matched)),
                ),
                reason = if (matched) definition.onPassed.description else definition.onFailed.description,
            )
        }

        private fun searchEvidenceSelection(
            clue: PlaceClue,
            candidates: List<PlaceCandidateSelector.Candidate>,
        ): CandidateSelection? {
            val clueName = clue.name.groundingKey()
            val contextualQueries = clue.searchQueries()
                .filter { query -> query.groundingKey() != clueName && query.groundingKey().length > clueName.length }
                .toSet()
            val eligible = candidates.filter { candidate ->
                candidate.matchedQueryRanks.count { (query, rank) -> query in contextualQueries && rank == 0 } >=
                    MIN_TOP_CONTEXTUAL_QUERY_SUPPORT && candidate.supportingProviders.size >= MIN_PROVIDER_SUPPORT
            }
            if (eligible.size != 1) return null
            val selected = eligible.single()
            val sameNameCandidates = candidates.filter { candidate ->
                candidate.place.name.normalizedName() == selected.place.name.normalizedName() &&
                    candidate.matchedQueries.any(contextualQueries::contains)
            }
            return selected.takeIf { sameNameCandidates.size == 1 }
                ?.let { CandidateSelection(it.place, "search_evidence") }
        }

        private fun result(
            selection: CandidateSelection?,
            compatibleCandidates: List<PlaceCandidateSelector.Candidate>,
            strictMatches: List<PlaceCandidateSelector.Candidate>,
            groundedCandidates: GroundedCandidateMatches,
            evaluations: List<ParsingRuleEvaluation>,
        ) = ParsingPolicyEvaluation(
            AutomaticResult(
                selection,
                compatibleCandidates,
                strictMatches,
                groundedCandidates.matches,
                groundedCandidates.explicitNameSearchMatch,
            ),
            evaluations,
        )

        private fun remainingAutomaticRules() = listOf(UNIQUE_STRICT_RULE, UNIQUE_GROUNDED_RULE, SEARCH_EVIDENCE_RULE)

        private fun ParsingRule<MatchFacts>.skipped(reason: String) = ParsingRuleEvaluation(
            definition.id,
            ParsingRuleOutcome.SKIPPED,
            emptyList(),
            reason,
            null,
        )
    }
}

internal data class CandidateSelection(val place: PlaceCandidate, val method: String)

private fun Collection<PlaceCandidateSelector.Candidate>.strictMatches(
    clue: PlaceClue,
): List<PlaceCandidateSelector.Candidate> {
    val normalizedName = clue.name.normalizedName()
    val normalizedRegion = clue.region?.normalizedName()?.takeIf(String::isNotEmpty)
    return filter { candidate ->
        candidate.place.name.normalizedName() == normalizedName &&
            (normalizedRegion == null || candidate.place.address.normalizedName().contains(normalizedRegion)) &&
            PlaceAddressMatcher.isCompatible(clue.addressHint, candidate.place.address)
    }
}

internal fun strictMatches(
    clue: PlaceClue,
    candidates: Collection<PlaceCandidateSelector.Candidate>,
): List<PlaceCandidateSelector.Candidate> = candidates.strictMatches(clue)

private fun Boolean.toOutcome() = if (this) ParsingRuleOutcome.PASSED else ParsingRuleOutcome.FAILED

private fun PlaceCandidate?.toOutcome() = (this != null).toOutcome()

private fun String.normalizedName(): String = lowercase().filterNot(Char::isWhitespace)

private fun String.groundingKey(): String = lowercase().filter(Char::isLetterOrDigit)

internal fun searchEvidenceCandidate(
    clue: PlaceClue,
    candidates: List<PlaceCandidateSelector.Candidate>,
): CandidateSelection? = CandidateResolutionPolicy()
    .evaluate(CandidateResolutionPolicy.Context(clue, candidates))
    .result.selection
