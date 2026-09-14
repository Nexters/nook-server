import { useState } from "react";
import { Accordion, AccordionDetails, AccordionSummary, Alert, Box, Chip, Stack, Switch, FormControlLabel, Typography } from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import type { ProcessingTrace, PipelineResponse } from "./ParsingPipeline";

const stageNames: Record<string, string> = { job: "실행 결과", extract: "원문 수집", inference: "본문 분석", complete: "저장", search: "장소 검색", select: "장소 후보 선택", match: "장소 후보 비교", "clue-text": "본문 장소 단서", "image-transcript": "이미지 문자 인식", ocr: "이미지 분석 결정", "clue-image": "이미지 장소 단서", "title-finalization": "제목 결정", "source-coverage": "장소 누락 확인" };
const isRule = (t: ProcessingTrace) => t.action === "place.rule.evaluated";
const isError = (t: ProcessingTrace) => !isRule(t) && ["failed", "failure"].includes(t.outcome.toLowerCase());
const detailNames: Record<string, string> = { reason: "사유", query: "검색어", queries: "검색어", placeName: "장소 이름", candidateCount: "검색 후보 수", strictMatchCount: "조건에 맞는 후보 수", addressCompatibleCount: "주소가 맞는 후보 수", groundedMatchCount: "근거가 일치하는 후보 수", provider: "조회 서비스", nextStepId: "다음 단계", ruleId: "규칙 식별자", ruleOutcome: "조건 판정", error: "오류", exception: "오류" };
const clean = (s: string) => s.replace(/https?:\/\/[^\s<>"']+/gi, "[원본 URL 숨김]").replace(/(Bearer\s+)[a-z0-9._~+/-]+/gi, "$1[숨김]").replace(/((?:token|password|secret|api[_-]?key|authorization)["']?\s*[:=]\s*)(?:"[^"]*"|'[^']*'|[^,;\s]+)/gi, "$1[숨김]");
function actionName(t: ProcessingTrace) {
  if (isRule(t)) return "조건 판정";
  if (t.action.endsWith("retry_scheduled")) return "자동 재시도 예약";
  if (t.action.endsWith("job.claimed")) return "실행 시작";
  if (t.action.endsWith("job.failed")) return "최종 실패";
  if (t.action.endsWith("job.completed")) return "실행 완료";
  if (t.action.endsWith("stage.failed")) return "단계 처리 오류";
  if (t.action.endsWith("stage.completed")) return "단계 완료";
  return ({ "place.search.result": "지도 검색 결과", "place.candidates.matched": "장소 후보 비교", "place.candidate.selected": "장소 후보 선택", "place.clue.rejected": "장소 단서 처리 오류", "content.inference.result": "본문 분석 결과" } as Record<string, string>)[t.action] ?? "처리 기록";
}

export function ProcessingTimeline({ traces, nodes }: { traces: ProcessingTrace[]; nodes: PipelineResponse["nodes"] }) {
  const [all, setAll] = useState(false);
  const latest = new Map<string, number>();
  traces.forEach(t => latest.set(t.flow, Math.max(latest.get(t.flow) ?? 0, t.attempt ?? 0)));
  const selected = [...traces].sort((a, b) => a.id - b.id).filter(t => all || (t.attempt ?? 0) === latest.get(t.flow));
  const runs = new Map<string, ProcessingTrace[]>();
  selected.forEach(t => { const key = `${t.flow}-${t.attempt ?? 0}`; runs.set(key, [...runs.get(key) ?? [], t]); });
  const titles = new Map(nodes.flatMap(n => n.decisions ?? []).filter(d => d.ruleId).map(d => [d.ruleId!, d.title]));
  return <Stack spacing={2}>
    <Box><Typography variant="h6">처리 흐름</Typography><Typography variant="body2" color="text.secondary">단계별 실행 결과를 따라가며 확인합니다. 조건에 해당하지 않거나 평가를 생략한 규칙은 처리 오류가 아닙니다.</Typography><FormControlLabel control={<Switch checked={all} onChange={e => setAll(e.target.checked)} />} label="이전 실행 이력도 보기" /></Box>
    {!traces.length && <Alert severity="info">저장된 상세 처리 이력이 없습니다. 위의 작업 상태와 실패 사유를 확인해 주세요.</Alert>}
    {[...runs.entries()].map(([key, run]) => <Box key={key}><Typography variant="subtitle1" sx={{ mb: 1 }}>{run[0].flow === "content" ? "본문 처리" : "장소 처리"} · {run[0].attempt ?? 1}차 실행</Typography><StageTimeline traces={run} titles={titles} /></Box>)}
  </Stack>;
}

function StageTimeline({ traces, titles }: { traces: ProcessingTrace[]; titles: Map<string, string> }) {
  // Preserve order: the same stage may run again after a fallback decision.
  const groups: ProcessingTrace[][] = [];
  traces.forEach(t => { if (groups.at(-1)?.[0].stage === t.stage) groups.at(-1)!.push(t); else groups.push([t]); });
  return <Stack spacing={1.2} sx={{ borderLeft: 2, borderColor: "divider", pl: 2 }}>
    {groups.map((group, i) => {
      const errors = group.filter(isError); const rules = group.filter(isRule); const operational = group.filter(t => !isRule(t));
      const skipped = rules.filter(t => t.outcome.toLowerCase() === "skipped");
      const skippedReasons = [...new Set(skipped.map(t => t.details.reason || "앞선 조건에 따라 평가하지 않음"))];
      return <Accordion key={group[0].id} defaultExpanded={errors.length > 0} variant="outlined" disableGutters>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}><Stack spacing={.5} sx={{ width: "100%" }}><Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}><Typography variant="subtitle2">{i + 1}. {stageNames[group[0].stage] ?? "처리 단계"}</Typography><Chip size="small" color={errors.length ? "error" : "default"} label={errors.length ? "처리 오류" : operational.length ? actionName(operational.at(-1)!) : "조건에 따라 경로 선택"} /></Stack>
          <Typography variant="body2" sx={{ overflowWrap: "anywhere" }}>{errors.length ? clean(errors.at(-1)!.details.reason ?? errors.at(-1)!.details.error ?? "아래 처리 기록에서 상세 내용을 확인하세요.") : skippedReasons.length ? `${clean(skippedReasons.join(" · "))} → 조건 ${skipped.length}개 평가 생략` : `${operational.length}개 처리 기록 · ${rules.length}개 조건 판정`}</Typography>
        </Stack></AccordionSummary>
        <AccordionDetails><Stack spacing={1.5}>
          {operational.map(t => <TraceDetail key={t.id} trace={t} />)}
          {rules.length > 0 && <Accordion variant="outlined" disableGutters><AccordionSummary expandIcon={<ExpandMoreIcon />}>조건 판정 {rules.length}건 · 생략 {skipped.length}건</AccordionSummary><AccordionDetails><Stack spacing={1.5}>
            {rules.filter(t => t.outcome.toLowerCase() !== "skipped").map(t => <Box key={t.id}><Typography variant="subtitle2">{titles.get(t.details.ruleId) ?? "정책 조건"} · {t.outcome.toLowerCase() === "passed" ? "해당함" : "해당하지 않음"}</Typography><Typography variant="body2">{clean(t.details.reason ?? "판정 사유 미기록")}</Typography><TraceFacts details={t.details} /></Box>)}
            {skippedReasons.map(reason => <Typography key={reason} variant="body2" color="text.secondary">{clean(reason)} → {skipped.filter(t => (t.details.reason || "앞선 조건에 따라 평가하지 않음") === reason).length}개 조건 평가 생략</Typography>)}
          </Stack></AccordionDetails></Accordion>}
        </Stack></AccordionDetails>
      </Accordion>;
    })}
  </Stack>;
}
function TraceDetail({ trace }: { trace: ProcessingTrace }) {
  return <Box><Typography variant="subtitle2" color={isError(trace) ? "error.main" : "text.primary"}>{actionName(trace)} · {new Date(trace.createdAt).toLocaleTimeString("ko-KR")}{trace.durationMs != null && ` · ${trace.durationMs}ms`}</Typography>{trace.details.reason && <Typography variant="body2" sx={{ overflowWrap: "anywhere" }}>{clean(trace.details.reason)}</Typography>}<TraceFacts details={trace.details} /></Box>;
}
function TraceFacts({ details }: { details: Record<string, string> }) {
  const rows = Object.entries(details).filter(([k, v]) => k !== "reason" && v);
  if (!rows.length) return null;
  return <Accordion disableGutters sx={{ boxShadow: "none", "&:before": { display: "none" } }}><AccordionSummary expandIcon={<ExpandMoreIcon />}>판정 근거·상세 값</AccordionSummary><AccordionDetails>{rows.map(([k, v]) => <Box key={k} sx={{ mb: 1 }}><Typography variant="caption" color="text.secondary">{detailNames[k] ?? (k.startsWith("fact.") ? `판정 입력 · ${k.slice(5)}` : k)}</Typography><Typography variant="body2" sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{/token|password|secret|api[_-]?key|authorization/i.test(k) ? "[숨김]" : clean(v)}</Typography></Box>)}</AccordionDetails></Accordion>;
}
