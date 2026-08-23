import AccountTreeIcon from "@mui/icons-material/AccountTree";
import CloseIcon from "@mui/icons-material/Close";
import SearchIcon from "@mui/icons-material/Search";
import { Accordion, AccordionDetails, AccordionSummary, Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Divider, Drawer, IconButton, Stack, Tab, Tabs, TextField, Typography } from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { Background, Controls, Handle, MarkerType, MiniMap, Position, ReactFlow, type Edge, type Node, type NodeProps } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "./api";
import "./parsing-pipeline.css";

type Rule = { label: string; value: string; description?: string };
type RuleSection = { title: string; description?: string; rules: Rule[] };
type DecisionStep = { order: number; title: string; condition: string; expression?: string; onPass: string; onFail: string; source: string; ruleId?: string };
type PipelineNode = {
  id: string;
  title: string;
  subtitle: string;
  lane: "CONTENT" | "PLACE" | "MEDIA";
  kind: string;
  position: { x: number; y: number };
  summary: string;
  inputs: string[];
  outputs: string[];
  stages: string[];
  configurationKeys: string[];
  decisions?: DecisionStep[];
  sections: RuleSection[];
  examples: string[];
};
type PipelineEdge = { id: string; source: string; target: string; label?: string; kind: string };
type RuntimeConfiguration = { key: string; configuredValue?: string; effectiveValue: string; source: string; description: string; warnings: string[] };
type UnresolvedPlaceClue = { clue: { name: string; region?: string }; reason: string; type?: "NOT_EXTRACTED" | "RESOLUTION_FAILED" };
type JobExecution = { status: string; stage?: string; progressPercent: number; attemptCount: number; failureReason?: string; nextAttemptAt?: string; outcome?: string; expectedPlaceCount?: number; extractedPlaceCount?: number; resolvedPlaceCount?: number; unresolvedPlaceClues?: UnresolvedPlaceClue[] };
type ProcessingTrace = { id: number; flow: string; stage: string; action: string; outcome: string; attempt?: number; durationMs?: number; details: Record<string, string>; createdAt: string };
type ParsingExecution = { postId: number; title?: string; content: JobExecution; place?: JobExecution; traces: ProcessingTrace[] };
type PipelineResponse = { nodes: PipelineNode[]; edges: PipelineEdge[]; configurations: RuntimeConfiguration[]; execution?: ParsingExecution };
type NodeState = "completed" | "active" | "failed" | "waiting" | "reference";
type FlowNodeData = { pipeline: PipelineNode; state: NodeState; configuration?: RuntimeConfiguration; [key: string]: unknown };

const laneLabels = { CONTENT: "콘텐츠 파싱", PLACE: "장소 파싱", MEDIA: "장소 사진 보강" };

function PipelineNodeCard({ data }: NodeProps<Node<FlowNodeData>>) {
  const { pipeline, state, configuration } = data;
  const stateLabel = state === "completed" ? "완료" : state === "active" ? "진행 중" : state === "failed" ? "실패" : state === "waiting" ? "대기" : "규칙";
  return <Box className={`pipeline-node pipeline-node--${state} ${pipeline.kind === "DECISION" ? "pipeline-node--decision" : ""}`}>
    <Handle type="target" position={Position.Left} />
    <Stack direction="row" sx={{ alignItems: "flex-start", justifyContent: "space-between", gap: 1 }}>
      <Box><Typography className="pipeline-node-lane">{laneLabels[pipeline.lane]}</Typography><Typography className="pipeline-node-title">{pipeline.title}</Typography></Box>
      <span className={`pipeline-state pipeline-state--${state}`}>{stateLabel}</span>
    </Stack>
    <Typography className="pipeline-node-subtitle">{pipeline.subtitle}</Typography>
    {configuration && <Box className="pipeline-config-preview"><span>현재 적용</span><strong>{configuration.effectiveValue}</strong></Box>}
    <Typography className="pipeline-node-hint">클릭하여 상세 규칙 보기</Typography>
    <Handle type="source" position={Position.Right} />
  </Box>;
}

const nodeTypes = { pipeline: PipelineNodeCard };

export function ParsingPipelinePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedPostId = searchParams.get("postId") ?? "";
  const [postId, setPostId] = useState(requestedPostId);
  const [data, setData] = useState<PipelineResponse>();
  const [selected, setSelected] = useState<PipelineNode>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    setLoading(true); setError("");
    const query = requestedPostId ? `?postId=${encodeURIComponent(requestedPostId)}` : "";
    api<PipelineResponse>(`/parsing-pipeline${query}`)
      .then(setData)
      .catch((cause: unknown) => setError(cause instanceof Error ? cause.message : "파이프라인을 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [requestedPostId]);

  const flow = useMemo(() => toFlow(data), [data]);
  const search = () => {
    const normalized = postId.trim();
    setSearchParams(normalized ? { postId: normalized } : {});
  };

  return <Stack spacing={2.5} className="pipeline-page">
    <Stack direction={{ xs: "column", lg: "row" }} spacing={2} sx={{ justifyContent: "space-between", alignItems: { lg: "flex-end" } }}>
      <Box><Typography variant="overline" color="primary.main" className="mono-text">PARSING EXPLORER</Typography><Typography variant="h4">파싱 파이프라인</Typography><Typography color="text.secondary" sx={{ mt: .5 }}>전체 처리 흐름을 탐색하고 각 단계의 실제 판정 규칙을 확인합니다.</Typography></Box>
      <Stack direction="row" spacing={1} component="form" onSubmit={(event) => { event.preventDefault(); search(); }}>
        <TextField size="small" label="게시글 ID" value={postId} onChange={(event) => setPostId(event.target.value)} slotProps={{ htmlInput: { inputMode: "numeric" } }} sx={{ width: 180 }} />
        <Button type="submit" variant="contained" startIcon={<SearchIcon />}>실행 상태 보기</Button>
        {requestedPostId && <Button variant="text" onClick={() => { setPostId(""); setSearchParams({}); }}>초기화</Button>}
      </Stack>
    </Stack>

    {error && <Alert severity="error">{error}</Alert>}
    {data?.execution && <ExecutionSummary execution={data.execution} />}
    {data?.execution && <ExecutionTimeline traces={data.execution.traces ?? []} />}
    {data && <ConfigurationStrip configurations={data.configurations} />}

    <Card variant="outlined" className="pipeline-canvas-card">
      <Box className="pipeline-lane-labels"><span>콘텐츠</span><span>장소</span><span>사진 보강</span></Box>
      {loading ? <Box className="pipeline-loading"><CircularProgress /></Box> : data && <ReactFlow
        nodes={flow.nodes}
        edges={flow.edges}
        nodeTypes={nodeTypes}
        onNodeClick={(_, node) => setSelected((node.data as FlowNodeData).pipeline)}
        defaultViewport={{ x: 72, y: 110, zoom: .72 }}
        minZoom={.25}
        maxZoom={1.8}
        nodesDraggable={false}
        nodesConnectable={false}
        elementsSelectable
      >
        <Background color="#d9dee7" gap={28} size={1} />
        <MiniMap pannable zoomable nodeColor={(node) => stateColor((node.data as FlowNodeData).state)} maskColor="rgba(244,245,247,.72)" />
        <Controls showInteractive={false} />
      </ReactFlow>}
    </Card>
    <RuleDrawer node={selected} configurations={data?.configurations ?? []} onClose={() => setSelected(undefined)} />
  </Stack>;
}

function ExecutionTimeline({ traces }: { traces: ProcessingTrace[] }) {
  return <Card variant="outlined"><CardContent>
    <Typography variant="h6">실제 처리 이력</Typography>
    <Typography variant="body2" color="text.secondary" sx={{ mt: .5, mb: 2 }}>검색어와 후보 수, 단계별 판정 및 실패 사유를 시간순으로 표시합니다.</Typography>
    {traces.length === 0 ? <Alert severity="info">상세 이력 저장 기능 배포 이전 게시물입니다. 위의 최종 상태와 실패 사유만 확인할 수 있습니다.</Alert> :
      <Stack spacing={1}>{traces.map((trace) => <TraceRow key={trace.id} trace={trace} />)}</Stack>}
  </CardContent></Card>;
}

function TraceRow({ trace }: { trace: ProcessingTrace }) {
  const outcome = trace.outcome.toLowerCase();
  const failed = outcome === "failure" || outcome === "failed";
  const skipped = outcome === "skipped";
  const detailEntries = Object.entries(trace.details ?? {}).filter(([, value]) => value !== "");
  return <Accordion variant="outlined" disableGutters sx={{ borderRadius: "10px !important", "&:before": { display: "none" } }}>
    <AccordionSummary expandIcon={<ExpandMoreIcon />}>
      <Stack direction={{ xs: "column", md: "row" }} spacing={1} sx={{ width: "100%", alignItems: { md: "center" } }}>
        <Chip size="small" color={failed ? "error" : skipped ? "default" : "success"} variant={failed ? "filled" : "outlined"} label={failed ? "불충족" : skipped ? "건너뜀" : "통과"} />
        <Box sx={{ flex: 1 }}><Typography variant="subtitle2">{traceLabel(trace.action, trace.stage)}</Typography><Typography variant="caption" color="text.secondary" className="mono-text">{trace.flow} · {trace.stage}</Typography></Box>
        <Typography variant="caption" color="text.secondary">{trace.attempt ? `${trace.attempt}차 시도 · ` : ""}{trace.durationMs !== undefined && trace.durationMs !== null ? `${trace.durationMs.toLocaleString()}ms · ` : ""}{new Date(trace.createdAt).toLocaleTimeString("ko-KR")}</Typography>
      </Stack>
    </AccordionSummary>
    <AccordionDetails>
      {detailEntries.length === 0 ? <Typography variant="body2" color="text.secondary">추가 상세 정보가 없습니다.</Typography> : <Stack spacing={1}>{detailEntries.map(([key, value]) => <Box key={key} className="trace-detail"><Typography variant="caption" color="text.secondary" className="mono-text">{detailLabel(key)}</Typography><Typography variant="body2" sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{value}</Typography></Box>)}</Stack>}
    </AccordionDetails>
  </Accordion>;
}

function traceLabel(action: string, stage: string) {
  const labels: Record<string, string> = {
    "content.job.claimed": "콘텐츠 파싱 시작", "content.job.completed": "콘텐츠 파싱 완료", "content.job.failed": "콘텐츠 파싱 실패", "content.job.retry_scheduled": "콘텐츠 파싱 재시도 예약",
    "content.inference.result": "AI 장소 단서 추출", "content.stage.completed": `${stageLabel(stage)} 완료`, "content.stage.failed": `${stageLabel(stage)} 실패`,
    "place.job.claimed": "장소 파싱 시작", "place.job.completed": "장소 파싱 완료", "place.job.failed": "장소 파싱 실패", "place.job.retry_scheduled": "장소 파싱 재시도 예약",
    "place.search.result": "지도 장소 검색 결과", "place.candidates.matched": "장소 후보 판정", "place.candidate.selected": "장소 후보 선택", "place.clue.rejected": "장소 단서 처리 실패", "place.rule.evaluated": "정책 규칙 판정", "place.stage.completed": `${stageLabel(stage)} 완료`, "place.stage.failed": `${stageLabel(stage)} 실패`,
  };
  return labels[action] ?? action;
}

function stageLabel(stage: string) { return ({ extract: "원문 수집", inference: "AI 추론", complete: "저장", search: "장소 검색", select: "후보 선택", "source-coverage": "원문 장소 커버리지", "title-finalization": "제목 결정", "clue-text": "텍스트 장소 추출", "image-transcript": "이미지 OCR", "clue-image": "이미지 장소 추출" } as Record<string, string>)[stage] ?? stage; }
function detailLabel(key: string) {
  if (key.startsWith("fact.")) return `입력 사실 · ${key.slice(5)}`;
  return ({ ruleId: "정책 ruleId", ruleOutcome: "판정 결과", nextStepId: "다음 단계", placeName: "추출 상호명", region: "추출 지역", addressHint: "추출 주소", queries: "검색어", query: "실행 검색어", candidateCount: "검색 후보 수", candidates: "후보 목록", addressCompatibleCount: "주소 호환 후보 수", strictMatchCount: "엄격 일치 수", groundedMatchCount: "근거 일치 수", reason: "판정 사유", name: "선택 장소", address: "선택 주소", provider: "Provider", method: "선택 방식", placeClueCount: "추출 장소 수", resolvedPlaceCount: "해결 장소 수", unresolvedPlaceCount: "미해결 장소 수" } as Record<string, string>)[key] ?? key;
}

function ExecutionSummary({ execution }: { execution: ParsingExecution }) {
  return <Card variant="outlined"><CardContent><Stack direction={{ xs: "column", md: "row" }} spacing={2} sx={{ alignItems: { md: "center" } }}>
    <Box sx={{ flex: 1 }}><Typography variant="overline" color="text.secondary">POST #{execution.postId}</Typography><Typography variant="h6">{execution.title ?? "제목 없음"}</Typography></Box>
    <JobSummary label="콘텐츠" job={execution.content} />
    <JobSummary label="장소" job={execution.place} />
  </Stack></CardContent></Card>;
}

function JobSummary({ label, job }: { label: string; job?: JobExecution }) {
  if (!job) return <Box className="job-summary"><span>{label}</span><strong>미시작</strong></Box>;
  return <Box className="job-summary"><span>{label} · 시도 {job.attemptCount}회</span><strong>{job.outcome ?? job.status} · {job.progressPercent}%</strong>{job.outcome === "PARTIAL" && <small className="job-failure">해결 {job.resolvedPlaceCount ?? 0}/{job.expectedPlaceCount ?? job.extractedPlaceCount ?? "?"}곳 · 미해결 {job.unresolvedPlaceClues?.map((item) => `[${unresolvedTypeLabel(item.type)}] ${item.clue.name}`).join(", ") || "진단 없음"}</small>}{job.stage && <small>{job.stage}</small>}{job.failureReason && <small className="job-failure">{job.failureReason}</small>}</Box>;
}

function unresolvedTypeLabel(type?: UnresolvedPlaceClue["type"]) {
  return type === "NOT_EXTRACTED" ? "단서 미추출" : "후보 선택 실패";
}

function ConfigurationStrip({ configurations }: { configurations: RuntimeConfiguration[] }) {
  return <Stack direction={{ xs: "column", lg: "row" }} spacing={1.5}>{configurations.map((configuration) => <Card variant="outlined" key={configuration.key} sx={{ flex: 1 }}><CardContent sx={{ py: 1.5, "&:last-child": { pb: 1.5 } }}><Stack direction="row" sx={{ justifyContent: "space-between", gap: 1 }}><Typography className="mono-text" variant="caption" color="text.secondary">{configuration.key}</Typography><Chip size="small" variant="outlined" label={configuration.source === "RUNTIME" ? "runtime" : "fallback"} /></Stack><Typography variant="subtitle2" sx={{ mt: .75 }}>{configuration.effectiveValue}</Typography>{configuration.warnings.map((warning) => <Typography key={warning} variant="caption" color="warning.main" sx={{ display: "block", mt: .5 }}>{warning}</Typography>)}</CardContent></Card>)}</Stack>;
}

function RuleDrawer({ node, configurations, onClose }: { node?: PipelineNode; configurations: RuntimeConfiguration[]; onClose: () => void }) {
  const [tab, setTab] = useState(0);
  const related = configurations.filter((configuration) => node?.configurationKeys.includes(configuration.key));
  const decisions = node?.decisions ?? [];
  useEffect(() => setTab(0), [node?.id]);
  return <Drawer anchor="right" open={Boolean(node)} onClose={onClose} slotProps={{ paper: { className: "rule-drawer" } }}>
    {node && <><Box className="rule-drawer-header"><Box><Typography variant="overline" color="primary.main">{laneLabels[node.lane]}</Typography><Typography variant="h5">{node.title}</Typography><Typography color="text.secondary">{node.subtitle}</Typography></Box><IconButton aria-label="상세 규칙 닫기" onClick={onClose}><CloseIcon /></IconButton></Box><Divider />
      <Tabs value={tab} onChange={(_, value: number) => setTab(value)} variant="fullWidth" aria-label="노드 상세 정보"><Tab label="개요" /><Tab label={`판정 흐름 ${decisions.length}`} /><Tab label="규칙·예시" /></Tabs><Divider />
      <Box className="rule-drawer-body">
        {tab === 0 && <><Typography>{node.summary}</Typography><IOSection title="입력" values={node.inputs} /><IOSection title="출력" values={node.outputs} />
          {related.map((configuration) => <Box className="rule-config-card" key={configuration.key}><Typography variant="caption" className="mono-text">{configuration.key}</Typography><Typography variant="subtitle2" sx={{ mt: .5 }}>실제 적용: {configuration.effectiveValue}</Typography><Typography variant="body2" color="text.secondary">저장값: {configuration.configuredValue ?? "없음"} · {configuration.source}</Typography>{configuration.warnings.map((warning) => <Alert severity="warning" key={warning} sx={{ mt: 1 }}>{warning}</Alert>)}</Box>)}
        </>}
        {tab === 1 && <DecisionFlow decisions={decisions} />}
        {tab === 2 && <>{node.sections.map((section) => <Box key={section.title} className="rule-section"><Typography variant="h6">{section.title}</Typography>{section.description && <Typography color="text.secondary">{section.description}</Typography>}<Stack spacing={1} sx={{ mt: 1.25 }}>{section.rules.map((rule) => <Box className="rule-row" key={`${section.title}-${rule.label}`}><Typography variant="body2" color="text.secondary">{rule.label}</Typography><Typography variant="subtitle2">{rule.value}</Typography>{rule.description && <Typography variant="caption" color="text.secondary">{rule.description}</Typography>}</Box>)}</Stack></Box>)}
          {node.examples.length > 0 && <Box className="rule-section"><Typography variant="h6">계산·판정 예시</Typography>{node.examples.map((example) => <Alert severity="info" key={example} sx={{ mt: 1 }}>{example}</Alert>)}</Box>}
          <Alert severity="info" icon={<AccountTreeIcon />} sx={{ mt: 2 }}>표시된 임계값과 조건식은 실제 판정 코드가 사용하는 규칙 스펙에서 조회합니다.</Alert>
        </>}
      </Box></>}
  </Drawer>;
}

function DecisionFlow({ decisions }: { decisions: DecisionStep[] }) {
  return <Box className="decision-flow">{decisions.map((decision) => <Box className="decision-step" key={`${decision.order}-${decision.title}`}>
    <span className="decision-order">{decision.order}</span>
    <Box className="decision-card"><Stack direction="row" sx={{ justifyContent: "space-between", gap: 1 }}><Typography variant="h6">{decision.title}</Typography><Stack direction="row" spacing={.5}>{decision.ruleId && <Chip size="small" variant="outlined" label={decision.ruleId} className="mono-text" />}<Chip size="small" label={decision.source} className="decision-source" /></Stack></Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>IF</Typography><Typography variant="subtitle2">{decision.condition}</Typography>
      {decision.expression && <Box className="decision-expression">{decision.expression}</Box>}
      <Stack direction={{ xs: "column", sm: "row" }} spacing={1} sx={{ mt: 1.25 }}><Box className="decision-result decision-result--pass"><span>통과</span>{decision.onPass}</Box><Box className="decision-result decision-result--fail"><span>불충족</span>{decision.onFail}</Box></Stack>
    </Box>
  </Box>)}</Box>;
}

function IOSection({ title, values }: { title: string; values: string[] }) {
  return <Box sx={{ mt: 2 }}><Typography variant="subtitle2">{title}</Typography><Stack direction="row" spacing={.75} useFlexGap sx={{ mt: .75, flexWrap: "wrap" }}>{values.map((value) => <Chip key={value} size="small" variant="outlined" label={value} />)}</Stack></Box>;
}

function toFlow(data?: PipelineResponse): { nodes: Node<FlowNodeData>[]; edges: Edge[] } {
  if (!data) return { nodes: [], edges: [] };
  const stageOrder = data.nodes.flatMap((node) => node.stages);
  const currentJob = data.execution?.place?.stage ? data.execution.place : data.execution?.content;
  const currentStageIndex = currentJob?.stage ? stageOrder.indexOf(currentJob.stage) : -1;
  const configs = new Map(data.configurations.map((configuration) => [configuration.key, configuration]));
  const nodes = data.nodes.map((pipeline): Node<FlowNodeData> => ({
    id: pipeline.id,
    type: "pipeline",
    position: pipeline.position,
    data: {
      pipeline,
      state: nodeState(pipeline, data.execution, currentJob, currentStageIndex, stageOrder),
      configuration: pipeline.configurationKeys.map((key) => configs.get(key)).find(Boolean),
    },
  }));
  const edges = data.edges.map((edge): Edge => ({
    ...edge,
    type: edge.kind === "skip" ? "smoothstep" : "default",
    animated: edge.kind === "async",
    markerEnd: { type: MarkerType.ArrowClosed, color: edgeColor(edge.kind) },
    style: { stroke: edgeColor(edge.kind), strokeWidth: 2, strokeDasharray: edge.kind === "skip" || edge.kind === "async" ? "6 5" : undefined },
    labelStyle: { fill: "#67707d", fontWeight: 700, fontSize: 11 },
    labelBgStyle: { fill: "#fff", fillOpacity: .9 },
  }));
  return { nodes, edges };
}

function nodeState(node: PipelineNode, execution: ParsingExecution | undefined, currentJob: JobExecution | undefined, currentStageIndex: number, stageOrder: string[]): NodeState {
  if (!execution) return "reference";
  if (node.id === "request") return "completed";
  if (node.stages.length === 0) {
    if (node.id === "image-decision" && currentStageIndex >= stageOrder.indexOf("PLACE_IMAGE_OCR")) return "completed";
    if (node.id === "thumbnail" && execution.place?.status === "COMPLETED") return "active";
    return "waiting";
  }
  const indexes = node.stages.map((stage) => stageOrder.indexOf(stage));
  if (currentJob?.stage && node.stages.includes(currentJob.stage)) return currentJob.status === "FAILED" ? "failed" : "active";
  if (currentStageIndex >= 0 && Math.max(...indexes) < currentStageIndex) return "completed";
  if (execution.content.status === "COMPLETED" && node.lane === "CONTENT") return "completed";
  if (execution.place?.status === "COMPLETED" && node.lane === "PLACE") return "completed";
  return "waiting";
}

function edgeColor(kind: string) { return kind === "conditional" ? "#9b6ae6" : kind === "async" ? "#2b9f78" : kind === "skip" ? "#99a0ac" : "#8aa7dd"; }
function stateColor(state: NodeState) { return state === "completed" ? "#2bae7f" : state === "active" ? "#558eff" : state === "failed" ? "#fa5947" : "#caced4"; }
