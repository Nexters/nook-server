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
import org.every.nook.api.application.processing.ParsingRuleId
import org.every.nook.api.application.processing.ParsingRuleOutcome
import org.every.nook.api.application.processing.ParsingStepDefinition
import org.every.nook.api.application.processing.ParsingStepId

internal class SourcePlaceCoveragePolicy :
    ParsingPolicy<SourcePlaceCoveragePolicy.Context, SourcePlaceCoveragePolicy.Result>(STEP) {
    override fun evaluatePolicy(context: Context): ParsingPolicyEvaluation<Result> {
        val items = context.body.numberedPlaceItems()
        val numberedListDetected = items.size >= MIN_NUMBERED_PLACE_COUNT
        val missingItems = if (numberedListDetected) items.unmatchedBy(context.clues) else emptyList()
        val numberedListEvaluation = NUMBERED_LIST_RULE.evaluate(items)
        val coverageEvaluation = EXTRACTION_COVERAGE_RULE.evaluate(items to missingItems)
        return ParsingPolicyEvaluation(
            Result(
                expectedPlaceCount = items.size.takeIf { numberedListDetected },
                missingItems = missingItems,
            ),
            listOf(numberedListEvaluation, coverageEvaluation),
        )
    }

    data class Context(val body: String?, val clues: List<PlaceClue>)

    data class Result(val expectedPlaceCount: Int?, val missingItems: List<SourcePlaceItem>) {
        fun unresolvedClues(): List<UnresolvedPlaceClue> = missingItems.map { item ->
            UnresolvedPlaceClue(
                clue = PlaceClue(
                    name = item.name,
                    region = null,
                    queries = listOfNotNull(item.username?.let { "@$it" }),
                ),
                reason = "Numbered source item was not extracted: ${item.order}. ${item.name}",
                type = UnresolvedPlaceClue.Type.NOT_EXTRACTED,
            )
        }
    }

    data class SourcePlaceItem(val order: Int, val name: String, val username: String?)

    companion object {
        val STEP by lazy {
            ParsingStepDefinition(
                id = ParsingStepId("source-coverage"),
                title = "원문 장소 목록 커버리지",
                description = "번호 목록의 각 장소가 파싱 단서로 추출됐는지 확인합니다.",
                inputs = listOf(
                    ParsingFieldDefinition("post-body", "게시글 본문"),
                    ParsingFieldDefinition("extracted-place-clues", "추출 장소 단서"),
                ),
                outputs = listOf(
                    ParsingFieldDefinition("expected-place-count", "예상 장소 수"),
                    ParsingFieldDefinition("missing-source-items", "미추출 원문 항목"),
                ),
                ruleIds = RULES.map(ParsingRuleDefinition::id),
            )
        }

        val RULES by lazy { listOf(NUMBERED_LIST_DEFINITION, EXTRACTION_COVERAGE_DEFINITION) }

        private val NUMBERED_LIST_DEFINITION = ParsingRuleDefinition(
            id = ParsingRuleId("place.source-coverage.numbered-list"),
            title = "연속 번호 목록 인식",
            description = "1번부터 연속된 두 개 이상의 장소 항목을 원문 장소 목록으로 인식합니다.",
            condition = "1부터 시작하는 연속 번호 항목이 두 개 이상인가?",
            expression = "numberedItems.size >= minimumNumberedPlaceCount",
            source = "SourcePlaceCoveragePolicy",
            parameters = mapOf(
                "minimum-numbered-place-count" to ParsingFactValue.Count(MIN_NUMBERED_PLACE_COUNT),
            ),
            onPassed = ParsingRuleEffect("번호 목록 수를 예상 장소 수로 사용"),
            onFailed = ParsingRuleEffect("기존 장소 수 추론 유지"),
        )
        private val EXTRACTION_COVERAGE_DEFINITION = ParsingRuleDefinition(
            id = ParsingRuleId("place.source-coverage.extraction-complete"),
            title = "번호 항목 추출 완전성",
            description = "번호 목록의 각 장소명 또는 username에 대응하는 장소 단서가 있는지 일대일로 확인합니다.",
            condition = "모든 번호 항목이 서로 다른 추출 장소 단서와 대응하는가?",
            expression = "missingItems.isEmpty()",
            source = "SourcePlaceCoveragePolicy",
            onPassed = ParsingRuleEffect("추출 커버리지 완료"),
            onFailed = ParsingRuleEffect("미추출 장소 진단 기록"),
        )

        private val NUMBERED_LIST_RULE = ParsingRule<List<SourcePlaceItem>>(NUMBERED_LIST_DEFINITION) { items ->
            val detected = items.size >= MIN_NUMBERED_PLACE_COUNT
            ParsingRuleDecision(
                outcome = detected.toRuleOutcome(),
                facts = listOf(
                    ParsingFact("source-item-count", "원문 번호 항목 수", ParsingFactValue.Count(items.size)),
                    ParsingFact(
                        "source-item-names",
                        "원문 번호 항목",
                        ParsingFactValue.TextList(items.map(SourcePlaceItem::name)),
                    ),
                ),
                reason = if (detected) "연속 번호 목록 인식" else "연속 번호 목록 없음",
            )
        }
        private val EXTRACTION_COVERAGE_RULE =
            ParsingRule<Pair<List<SourcePlaceItem>, List<SourcePlaceItem>>>(EXTRACTION_COVERAGE_DEFINITION) {
                val (items, missingItems) = it
                ParsingRuleDecision(
                    outcome = missingItems.isEmpty().toRuleOutcome(),
                    facts = listOf(
                        ParsingFact("source-item-count", "원문 번호 항목 수", ParsingFactValue.Count(items.size)),
                        ParsingFact(
                            "missing-source-items",
                            "미추출 원문 항목",
                            ParsingFactValue.TextList(missingItems.map { item -> "${item.order}. ${item.name}" }),
                        ),
                    ),
                    reason = if (missingItems.isEmpty()) "모든 원문 항목 추출" else "일부 원문 항목 미추출",
                )
            }
    }
}

private fun String?.numberedPlaceItems(): List<SourcePlaceCoveragePolicy.SourcePlaceItem> {
    val parsed = this.orEmpty().lineSequence().mapNotNull { line ->
        NUMBERED_PLACE_ITEM_PATTERN.matchEntire(line)?.let { match ->
            val order = match.groupValues[1].toIntOrNull() ?: return@let null
            val content = match.groupValues[2].trim()
            val username = USERNAME_PATTERN.find(content)?.groupValues?.get(1)
            val name = content.substringBefore('@').trim().trimEnd('-', '·', ':').trim()
            name.takeIf(String::isNotBlank)?.let {
                SourcePlaceCoveragePolicy.SourcePlaceItem(order, it, username)
            }
        }
    }.toList()
    val byOrder = parsed.associateBy(SourcePlaceCoveragePolicy.SourcePlaceItem::order)
    return buildList {
        for (order in 1..MAX_NUMBERED_PLACE_COUNT) {
            add(byOrder[order] ?: break)
        }
    }
}

private fun List<SourcePlaceCoveragePolicy.SourcePlaceItem>.unmatchedBy(
    clues: List<PlaceClue>,
): List<SourcePlaceCoveragePolicy.SourcePlaceItem> {
    val remainingClues = clues.toMutableList()
    val unmatched = toMutableList()
    unmatched.toList().forEach { item ->
        remainingClues.firstOrNull { clue -> clue.matchesName(item.name) }?.let { matched ->
            remainingClues.remove(matched)
            unmatched.remove(item)
        }
    }
    unmatched.toList().forEach { item ->
        val username = item.username ?: return@forEach
        remainingClues.firstOrNull { clue -> clue.matchesUsername(username) }?.let { matched ->
            remainingClues.remove(matched)
            unmatched.remove(item)
        }
    }
    return unmatched
}

private fun PlaceClue.matchesName(name: String): Boolean {
    val sourceKey = name.coverageKey().takeIf { it.length >= MIN_COVERAGE_KEY_LENGTH } ?: return false
    return (sequenceOf(this.name) + queries.asSequence())
        .map(String::coverageKey)
        .any { clueKey -> clueKey.contains(sourceKey) || sourceKey.contains(clueKey) }
}

private fun PlaceClue.matchesUsername(username: String): Boolean {
    val usernameKey = username.coverageKey()
    return (sequenceOf(name) + queries.asSequence()).map(String::coverageKey).any { it == usernameKey }
}

private fun String.coverageKey(): String = lowercase().filter(Char::isLetterOrDigit)

private fun Boolean.toRuleOutcome() = if (this) ParsingRuleOutcome.PASSED else ParsingRuleOutcome.FAILED

private val NUMBERED_PLACE_ITEM_PATTERN = Regex("""^\s*(\d{1,2})[.)]\s+(.+?)\s*$""")
private val USERNAME_PATTERN = Regex("""@([A-Za-z0-9._]+)""")
private const val MIN_NUMBERED_PLACE_COUNT = 2
private const val MIN_COVERAGE_KEY_LENGTH = 2
private const val MAX_NUMBERED_PLACE_COUNT = 60
