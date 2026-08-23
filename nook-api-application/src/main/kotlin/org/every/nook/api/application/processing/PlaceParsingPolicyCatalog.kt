package org.every.nook.api.application.processing

import org.every.nook.api.application.place.CandidateResolutionPolicy
import org.every.nook.api.application.place.ImageAnalysisPolicy
import org.every.nook.api.application.place.TextClueGroundingPolicy
import org.every.nook.api.application.post.PostTitleFinalizationPolicy

object PlaceParsingPolicyCatalog {
    val workflow: ParsingWorkflowDefinition by lazy {
        ParsingWorkflowDefinition(
            id = ParsingWorkflowId("place-parsing"),
            title = "장소 파싱 정책",
            description = "장소 단서 검증부터 제목 확정까지 실제 실행되는 판정 정책입니다.",
            steps = listOf(
                TextClueGroundingPolicy.STEP,
                CandidateResolutionPolicy.STEP,
                ImageAnalysisPolicy.STEP,
                CandidateResolutionPolicy.STEP.copy(
                    id = ParsingStepId("image-resolution"),
                    title = "이미지 장소 후보 판정",
                    description = "이미지 OCR에서 복원한 장소 단서를 동일한 후보 선택 정책으로 판정합니다.",
                ),
                PostTitleFinalizationPolicy.STEP,
            ),
            edges = listOf(
                edge("text-clues", "text-resolution"),
                edge("text-resolution", "image-decision"),
                edge("image-decision", "image-resolution", "이미지 분석 필요"),
                edge("image-decision", "title-finalization", "텍스트 결과 충분"),
                edge("image-resolution", "title-finalization"),
            ),
        )
    }

    val rules: List<ParsingRuleDefinition> by lazy {
        listOf(
            TextClueGroundingPolicy.RULES,
            CandidateResolutionPolicy.RULES,
            ImageAnalysisPolicy.RULES,
            PostTitleFinalizationPolicy.RULES,
        ).flatten()
    }

    val catalog: ParsingRuleCatalog by lazy { ParsingRuleCatalog(listOf(workflow), rules) }

    private fun edge(source: String, target: String, label: String? = null) = ParsingWorkflowEdgeDefinition(
        id = "$source-$target",
        source = ParsingStepId(source),
        target = ParsingStepId(target),
        label = label,
    )
}
