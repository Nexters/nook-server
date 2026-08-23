package org.every.nook.api.application.place

import org.every.nook.api.application.content.SourceProfileHint
import org.every.nook.api.application.processing.ParsingFact
import org.every.nook.api.application.processing.ParsingFactValue
import org.every.nook.api.application.processing.ParsingFieldDefinition
import org.every.nook.api.application.processing.ParsingPolicy
import org.every.nook.api.application.processing.ParsingPolicyEvaluation
import org.every.nook.api.application.processing.ParsingRule
import org.every.nook.api.application.processing.ParsingRuleDecision
import org.every.nook.api.application.processing.ParsingRuleDefinition
import org.every.nook.api.application.processing.ParsingRuleEffect
import org.every.nook.api.application.processing.ParsingRuleId
import org.every.nook.api.application.processing.ParsingRuleOutcome
import org.every.nook.api.application.processing.ParsingStepDefinition
import org.every.nook.api.application.processing.ParsingStepId

internal class TextClueGroundingPolicy : ParsingPolicy<TextClueGroundingPolicy.Context, Boolean>(STEP) {
    override fun evaluatePolicy(context: Context): ParsingPolicyEvaluation<Boolean> {
        val bodyEvaluation = BODY_GROUNDING_RULE.evaluate(context)
        if (bodyEvaluation.outcome == ParsingRuleOutcome.PASSED) {
            return ParsingPolicyEvaluation(
                true,
                listOf(bodyEvaluation, PROFILE_GROUNDING_RULE.skipped("본문 또는 해시태그 근거가 확인됨")),
            )
        }
        val profileEvaluation = PROFILE_GROUNDING_RULE.evaluate(context)
        return ParsingPolicyEvaluation(
            profileEvaluation.outcome == ParsingRuleOutcome.PASSED,
            listOf(bodyEvaluation, profileEvaluation),
        )
    }

    data class Context(
        val clue: PlaceClue,
        val body: String?,
        val hashtags: List<String>,
        val sourceProfileHints: List<SourceProfileHint>,
    )

    companion object {
        const val MIN_GROUNDING_KEY_LENGTH = 2

        val STEP by lazy {
            ParsingStepDefinition(
                id = ParsingStepId("text-clues"),
                title = "텍스트 단서 검증",
                description = "장소 단서가 실제 원문 또는 명시적으로 언급된 프로필에 근거하는지 확인합니다.",
                inputs = listOf(
                    ParsingFieldDefinition("text-place-clues", "텍스트 장소 단서"),
                    ParsingFieldDefinition("body", "본문"),
                    ParsingFieldDefinition("hashtags", "해시태그"),
                    ParsingFieldDefinition("source-profile-hints", "프로필 힌트"),
                ),
                outputs = listOf(ParsingFieldDefinition("grounded-place-clues", "근거가 확인된 단서")),
                ruleIds = RULES.map(ParsingRuleDefinition::id),
            )
        }

        val RULES by lazy { listOf(BODY_GROUNDING_DEFINITION, PROFILE_GROUNDING_DEFINITION) }

        private val BODY_GROUNDING_DEFINITION = ParsingRuleDefinition(
            id = ParsingRuleId("place.text-clue.body-grounding"),
            title = "본문·해시태그 근거",
            description = "정규화한 장소명 또는 검색어가 본문이나 해시태그에 포함되는지 확인합니다.",
            condition = "최소 길이 이상의 장소명 또는 검색어가 본문·해시태그에 존재하는가?",
            expression = "source.contains(clueKey) && clueKey.length >= minimumGroundingKeyLength",
            source = "TextClueGroundingPolicy",
            parameters = mapOf(
                "minimum-grounding-key-length" to ParsingFactValue.Count(MIN_GROUNDING_KEY_LENGTH),
            ),
            onPassed = ParsingRuleEffect("검색 대상 단서로 유지"),
            onFailed = ParsingRuleEffect("프로필 언급 근거 확인"),
        )

        private val PROFILE_GROUNDING_DEFINITION = ParsingRuleDefinition(
            id = ParsingRuleId("place.text-clue.profile-grounding"),
            title = "본문 언급 프로필 근거",
            description = "본문에 실제 등장한 username과 profile displayName이 장소명을 지지하는지 확인합니다.",
            condition = "본문에 @username이 있고 displayName이 추출 장소명과 일치하는가?",
            expression = "body.contains(@username) && displayName.matches(clueName)",
            source = "TextClueGroundingPolicy",
            parameters = mapOf(
                "minimum-grounding-key-length" to ParsingFactValue.Count(MIN_GROUNDING_KEY_LENGTH),
            ),
            onPassed = ParsingRuleEffect("검색 대상 단서로 유지"),
            onFailed = ParsingRuleEffect("텍스트 단서에서 제거"),
        )

        private val BODY_GROUNDING_RULE = ParsingRule<Context>(BODY_GROUNDING_DEFINITION) { context ->
            val grounded = context.clue.isGroundedIn(context.body, context.hashtags)
            ParsingRuleDecision(
                outcome = grounded.toRuleOutcome(),
                facts = listOf(
                    ParsingFact("place-name", "추출 장소명", ParsingFactValue.Text(context.clue.name)),
                    ParsingFact("grounded", "근거 확인", ParsingFactValue.Flag(grounded)),
                ),
                reason = if (grounded) "본문 또는 해시태그 근거 확인" else "본문·해시태그 근거 없음",
            )
        }

        private val PROFILE_GROUNDING_RULE = ParsingRule<Context>(PROFILE_GROUNDING_DEFINITION) { context ->
            val mentionedProfiles = context.sourceProfileHints.filter { hint ->
                context.body.orEmpty().contains("@${hint.username}", ignoreCase = true)
            }
            val clueKey = context.clue.name.groundingKey()
            val grounded = clueKey.length >= MIN_GROUNDING_KEY_LENGTH && mentionedProfiles.any { hint ->
                val displayNameKey = hint.displayName.groundingKey()
                displayNameKey.contains(clueKey) || clueKey.contains(displayNameKey)
            }
            ParsingRuleDecision(
                outcome = grounded.toRuleOutcome(),
                facts = listOf(
                    ParsingFact(
                        "mentioned-profiles",
                        "본문 언급 프로필",
                        ParsingFactValue.TextList(mentionedProfiles.map(SourceProfileHint::username)),
                    ),
                    ParsingFact("grounded", "근거 확인", ParsingFactValue.Flag(grounded)),
                ),
                reason = if (grounded) "본문에서 언급된 프로필 근거 확인" else "프로필 근거 없음",
            )
        }

        private fun ParsingRule<Context>.skipped(reason: String) =
            org.every.nook.api.application.processing.ParsingRuleEvaluation(
                definition.id,
                ParsingRuleOutcome.SKIPPED,
                emptyList(),
                reason,
                null,
            )
    }
}

private fun Boolean.toRuleOutcome(): ParsingRuleOutcome =
    if (this) ParsingRuleOutcome.PASSED else ParsingRuleOutcome.FAILED

private fun String.groundingKey(): String = lowercase().filter(Char::isLetterOrDigit)
