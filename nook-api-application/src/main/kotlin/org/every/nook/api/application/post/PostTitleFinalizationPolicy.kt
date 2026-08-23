package org.every.nook.api.application.post

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

internal class PostTitleFinalizationPolicy : ParsingPolicy<PostTitleFinalizationPolicy.Context, String>(STEP) {
    override fun evaluatePolicy(context: Context): ParsingPolicyEvaluation<String> {
        val selectedTitle = context.selected?.title.validPostTitle()
            ?.take(MAX_FINAL_TITLE_LENGTH)
            ?.takeUnless { context.selected?.source == PostTitleSelector.Source.NONE }
            ?.takeIf { it.hasConsistentPlaceCount(context.request.declaredPlaceCount, context.request.places.size) }
        val selectedEvaluation = SELECTED_TITLE_RULE.evaluate(context to selectedTitle)
        if (selectedEvaluation.outcome == ParsingRuleOutcome.PASSED) {
            return ParsingPolicyEvaluation(
                requireNotNull(selectedTitle),
                listOf(
                    selectedEvaluation,
                    FALLBACK_TITLE_RULE.skipped("모델 제목 채택"),
                    DEFAULT_TITLE_RULE.skipped("모델 제목 채택"),
                ),
            )
        }
        val fallbackTitle = fallbackPostTitle(context.request)
        val fallbackEvaluation = FALLBACK_TITLE_RULE.evaluate(context to fallbackTitle)
        if (fallbackEvaluation.outcome == ParsingRuleOutcome.PASSED) {
            return ParsingPolicyEvaluation(
                requireNotNull(fallbackTitle),
                listOf(selectedEvaluation, fallbackEvaluation, DEFAULT_TITLE_RULE.skipped("fallback 제목 채택")),
            )
        }
        return ParsingPolicyEvaluation(
            DEFAULT_POST_TITLE,
            listOf(selectedEvaluation, fallbackEvaluation, DEFAULT_TITLE_RULE.evaluate(context)),
        )
    }

    data class Context(val request: PostTitleSelector.Request, val selected: PostTitleSelector.Result?)

    companion object {
        val STEP by lazy {
            ParsingStepDefinition(
                id = ParsingStepId("title-finalization"),
                title = "제목 최종 결정",
                description = "모델 제목을 검증하고 deterministic fallback과 기본 제목 순으로 결정합니다.",
                inputs = listOf(
                    ParsingFieldDefinition("post-content", "본문·해시태그·위치 태그"),
                    ParsingFieldDefinition("cover-texts", "커버 OCR"),
                    ParsingFieldDefinition("resolved-places", "확정 장소"),
                ),
                outputs = listOf(ParsingFieldDefinition("final-title", "최종 제목")),
                ruleIds = RULES.map(ParsingRuleDefinition::id),
            )
        }

        val RULES by lazy {
            listOf(SELECTED_TITLE_DEFINITION, FALLBACK_TITLE_DEFINITION, DEFAULT_TITLE_DEFINITION)
        }

        private val SELECTED_TITLE_DEFINITION = definition(
            "place.title.selected-result",
            "모델 제목 검증",
            "모델 제목이 유효하고 source가 NONE이 아니며 장소 개수가 일치하는가?",
            "validTitle && source != NONE && titleCount == resolvedPlaceCount",
            "모델 제목 채택",
            "deterministic fallback 확인",
        )
        private val FALLBACK_TITLE_DEFINITION = definition(
            "place.title.deterministic-fallback",
            "Deterministic fallback",
            "본문 또는 확정 장소에서 안전한 fallback 제목을 만들 수 있는가?",
            "fallbackPostTitle(request) != null",
            "fallback 제목 채택",
            "기본 제목 사용",
        )
        private val DEFAULT_TITLE_DEFINITION = definition(
            "place.title.default",
            "기본 제목",
            "모델 제목과 fallback 제목이 모두 없는가?",
            "selectedTitle == null && fallbackTitle == null",
            "기본 제목 사용",
            "앞선 제목 유지",
        )

        private val SELECTED_TITLE_RULE = ParsingRule<Pair<Context, String?>>(SELECTED_TITLE_DEFINITION) {
            val (context, selectedTitle) = it
            ParsingRuleDecision(
                outcome = (selectedTitle != null).toOutcome(),
                facts = context.facts() + listOf(
                    ParsingFact("selected-title", "모델 제목", ParsingFactValue.Text(selectedTitle.orEmpty())),
                    ParsingFact(
                        "selected-source",
                        "제목 source",
                        ParsingFactValue.Text(context.selected?.source?.name ?: "없음"),
                    ),
                ),
                reason = if (selectedTitle != null) "모델 제목 검증 통과" else "모델 제목 검증 실패",
            )
        }
        private val FALLBACK_TITLE_RULE = ParsingRule<Pair<Context, String?>>(FALLBACK_TITLE_DEFINITION) {
            val (context, fallbackTitle) = it
            ParsingRuleDecision(
                outcome = (fallbackTitle != null).toOutcome(),
                facts = context.facts() +
                    ParsingFact("fallback-title", "Fallback 제목", ParsingFactValue.Text(fallbackTitle.orEmpty())),
                reason = if (fallbackTitle != null) "Fallback 제목 생성" else "Fallback 제목 없음",
            )
        }
        private val DEFAULT_TITLE_RULE = ParsingRule<Context>(DEFAULT_TITLE_DEFINITION) { context ->
            ParsingRuleDecision(
                outcome = ParsingRuleOutcome.PASSED,
                facts = context.facts() +
                    ParsingFact("default-title", "기본 제목", ParsingFactValue.Text(DEFAULT_POST_TITLE)),
                reason = "기본 제목 사용",
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
            "PostTitleFinalizationPolicy",
            onPassed = ParsingRuleEffect(onPassed),
            onFailed = ParsingRuleEffect(onFailed),
        )

        private fun Context.facts(): List<ParsingFact> = listOf(
            ParsingFact("resolved-place-count", "확정 장소 수", ParsingFactValue.Count(request.places.size)),
            ParsingFact(
                "declared-place-count",
                "본문 선언 장소 수",
                ParsingFactValue.Text(request.declaredPlaceCount?.toString() ?: "없음"),
            ),
        )

        private fun <C> ParsingRule<C>.skipped(reason: String) = ParsingRuleEvaluation(
            definition.id,
            ParsingRuleOutcome.SKIPPED,
            emptyList(),
            reason,
            null,
        )
    }
}

private fun Boolean.toOutcome() = if (this) ParsingRuleOutcome.PASSED else ParsingRuleOutcome.FAILED
