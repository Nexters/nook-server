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

internal class ImageAnalysisPolicy : ParsingPolicy<ImageAnalysisPolicy.Context, ImageAnalysisPolicy.Decision>(STEP) {
    @Suppress("ReturnCount") // Each return is a terminal rule outcome in the ordered policy.
    override fun evaluatePolicy(context: Context): ParsingPolicyEvaluation<Decision> {
        val evaluations = mutableListOf<ParsingRuleEvaluation>()
        val imageExists = IMAGE_EXISTS_RULE.evaluate(context).also(evaluations::add)
        if (imageExists.outcome == ParsingRuleOutcome.FAILED) {
            evaluations += remainingRules().map { it.skipped("분석할 이미지 없음") }
            return ParsingPolicyEvaluation(Decision(false, "no_images"), evaluations)
        }
        val noTextClue = NO_TEXT_CLUE_RULE.evaluate(context).also(evaluations::add)
        if (noTextClue.outcome == ParsingRuleOutcome.PASSED) {
            evaluations += listOf(NO_TEXT_RESOLVED_RULE, EXPECTED_COUNT_SHORTFALL_RULE)
                .map { it.skipped("앞선 이미지 분석 조건 충족") }
            return ParsingPolicyEvaluation(Decision(true, "no_text_place_clue"), evaluations)
        }
        val noTextResolved = NO_TEXT_RESOLVED_RULE.evaluate(context).also(evaluations::add)
        if (noTextResolved.outcome == ParsingRuleOutcome.PASSED) {
            evaluations += EXPECTED_COUNT_SHORTFALL_RULE.skipped("앞선 이미지 분석 조건 충족")
            return ParsingPolicyEvaluation(Decision(true, "no_text_place_resolved"), evaluations)
        }
        val shortfall = EXPECTED_COUNT_SHORTFALL_RULE.evaluate(context).also(evaluations::add)
        return ParsingPolicyEvaluation(
            Decision(
                required = shortfall.outcome == ParsingRuleOutcome.PASSED,
                reason = if (shortfall.outcome == ParsingRuleOutcome.PASSED) {
                    "expected_place_clue_shortfall"
                } else {
                    "text_place_clues_sufficient"
                },
            ),
            evaluations,
        )
    }

    data class Context(
        val textClueCount: Int,
        val textResolvedCount: Int,
        val expectedPlaceCount: Int?,
        val imageCount: Int,
    )

    data class Decision(val required: Boolean, val reason: String)

    companion object {
        val STEP by lazy {
            ParsingStepDefinition(
                id = ParsingStepId("image-decision"),
                title = "이미지 분석 판단",
                description = "텍스트만으로 장소를 충분히 찾았는지 판단해 이미지 OCR 실행 여부를 결정합니다.",
                inputs = listOf(
                    ParsingFieldDefinition("text-clue-count", "텍스트 단서 수"),
                    ParsingFieldDefinition("text-resolved-count", "텍스트 해결 수"),
                    ParsingFieldDefinition("expected-place-count", "예상 장소 수"),
                    ParsingFieldDefinition("image-count", "이미지 수"),
                ),
                outputs = listOf(ParsingFieldDefinition("image-analysis-required", "이미지 분석 필요 여부")),
                ruleIds = RULES.map(ParsingRuleDefinition::id),
            )
        }

        val RULES by lazy {
            listOf(
                IMAGE_EXISTS_DEFINITION,
                NO_TEXT_CLUE_DEFINITION,
                NO_TEXT_RESOLVED_DEFINITION,
                EXPECTED_COUNT_SHORTFALL_DEFINITION,
            )
        }

        private val IMAGE_EXISTS_DEFINITION = definition(
            "place.image-analysis.image-exists",
            "이미지 존재",
            "이미지가 한 장 이상 존재하는가?",
            "imageCount > 0",
            "텍스트 충족도 검사",
            "이미지 분석 생략",
        )
        private val NO_TEXT_CLUE_DEFINITION = definition(
            "place.image-analysis.no-text-clue",
            "텍스트 단서 없음",
            "텍스트 장소 단서가 없는가?",
            "textClueCount == 0",
            "이미지 OCR 실행",
            "텍스트 해결 수 검사",
        )
        private val NO_TEXT_RESOLVED_DEFINITION = definition(
            "place.image-analysis.no-text-resolved",
            "텍스트 해결 결과 없음",
            "텍스트에서 해결된 장소가 없는가?",
            "textResolvedCount == 0",
            "이미지 OCR 실행",
            "예상 장소 수 검사",
        )
        private val EXPECTED_COUNT_SHORTFALL_DEFINITION = definition(
            "place.image-analysis.expected-count-shortfall",
            "예상 장소 수 부족",
            "예상 장소 수보다 텍스트 단서가 적은가?",
            "expectedPlaceCount != null && textClueCount < expectedPlaceCount",
            "이미지 OCR 실행",
            "제목 확정으로 이동",
        )

        private val IMAGE_EXISTS_RULE = booleanRule(IMAGE_EXISTS_DEFINITION) { it.imageCount > 0 }
        private val NO_TEXT_CLUE_RULE = booleanRule(NO_TEXT_CLUE_DEFINITION) { it.textClueCount == 0 }
        private val NO_TEXT_RESOLVED_RULE = booleanRule(NO_TEXT_RESOLVED_DEFINITION) { it.textResolvedCount == 0 }
        private val EXPECTED_COUNT_SHORTFALL_RULE = booleanRule(EXPECTED_COUNT_SHORTFALL_DEFINITION) {
            it.expectedPlaceCount?.let { expected -> it.textClueCount < expected } == true
        }

        private fun definition(
            id: String,
            title: String,
            condition: String,
            expression: String,
            onPassed: String,
            onFailed: String,
        ) = ParsingRuleDefinition(
            id = ParsingRuleId(id),
            title = title,
            description = condition,
            condition = condition,
            expression = expression,
            source = "ImageAnalysisPolicy",
            onPassed = ParsingRuleEffect(onPassed),
            onFailed = ParsingRuleEffect(onFailed),
        )

        private fun booleanRule(definition: ParsingRuleDefinition, predicate: (Context) -> Boolean) =
            ParsingRule<Context>(definition) { context ->
                val matched = predicate(context)
                ParsingRuleDecision(
                    outcome = if (matched) ParsingRuleOutcome.PASSED else ParsingRuleOutcome.FAILED,
                    facts = context.facts() +
                        ParsingFact("condition-matched", "조건 충족", ParsingFactValue.Flag(matched)),
                    reason = if (matched) definition.onPassed.description else definition.onFailed.description,
                )
            }

        private fun Context.facts(): List<ParsingFact> = listOf(
            ParsingFact("text-clue-count", "텍스트 단서 수", ParsingFactValue.Count(textClueCount)),
            ParsingFact("text-resolved-count", "텍스트 해결 수", ParsingFactValue.Count(textResolvedCount)),
            ParsingFact(
                "expected-place-count",
                "예상 장소 수",
                ParsingFactValue.Text(expectedPlaceCount?.toString() ?: "없음"),
            ),
            ParsingFact("image-count", "이미지 수", ParsingFactValue.Count(imageCount)),
        )

        private fun remainingRules() = listOf(
            NO_TEXT_CLUE_RULE,
            NO_TEXT_RESOLVED_RULE,
            EXPECTED_COUNT_SHORTFALL_RULE,
        )

        private fun ParsingRule<Context>.skipped(reason: String) = ParsingRuleEvaluation(
            definition.id,
            ParsingRuleOutcome.SKIPPED,
            emptyList(),
            reason,
            null,
        )
    }
}
