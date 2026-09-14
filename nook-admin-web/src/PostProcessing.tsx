import { useEffect, useState, type ReactNode } from "react";
import { Link as RouterLink, useLocation, useParams, useSearchParams } from "react-router-dom";
import { Accordion, AccordionDetails, AccordionSummary, Alert, Box, Button, Card, CardActionArea, CardContent, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Pagination, Stack, Tab, Tabs, TextField, Typography } from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { api } from "./api";
import { type RecoveryPost, failedJobs } from "./ParsingPostRecovery";
import { recoveryLabel, recoveryStageNames, recoveryTypeNames, type RecoveryJob } from "./ParsingRecoveryTracking";
import { type PipelineResponse } from "./ParsingPipeline";
import { ProcessingTimeline } from "./ProcessingTimeline";

const categories = { INITIAL_FAILED: "최근 실패", RETRY_FAILED: "관리자 재시도 실패", ACTIVE: "처리 중", COMPLETED: "완료", UNPROCESSABLE: "처리 불가" };
const date = (v?: string) => v ? new Date(v).toLocaleString("ko-KR") : "과거 이력 · 시각 미기록";
const active = (p: RecoveryPost) => p.jobs.some(j => ["PENDING", "PROCESSING"].includes(j.status));
const lastFailure = (p: RecoveryPost) => failedJobs(p).map(j => j.lastFailedAt).filter((v): v is string => !!v).sort().at(-1);
const state = (p: RecoveryPost) => p.disposition === "UNPROCESSABLE" ? "처리 불가" : active(p) ? "처리 중" : failedJobs(p).some(j => j.recoveryStatus === "RETRY_FAILED") ? "관리자 재시도 실패" : failedJobs(p).length ? "실패" : p.jobs.some(j => j.recoveryStatus === "RECOVERED") ? "재시도 후 복구 완료" : "완료";

export function PostManagementList({ allPosts }: { allPosts: ReactNode }) {
  const [params, setParams] = useSearchParams();
  const category = params.get("category") ?? "ALL";
  return <Stack spacing={2}>
    <Box><Typography variant="h4">게시글 관리</Typography><Typography color="text.secondary">게시글을 선택하면 해당 게시글의 정보와 처리 상태를 확인할 수 있습니다.</Typography></Box>
    <Tabs value={category} variant="scrollable" scrollButtons="auto" onChange={(_, v: string) => setParams(v === "ALL" ? {} : { category: v, days: v.includes("FAILED") ? "7" : "all" })}>
      <Tab value="ALL" label="전체 게시글" />{Object.entries(categories).map(([value, label]) => <Tab key={value} value={value} label={label} />)}
    </Tabs>
    {category === "ALL" ? allPosts : <ProcessingList key={category} category={category} />}
  </Stack>;
}

function ProcessingList({ category }: { category: string }) {
  const [params, setParams] = useSearchParams();
  const periodFilter = category.includes("FAILED") || category === "UNPROCESSABLE";
  const days = periodFilter ? params.get("days") ?? (category.includes("FAILED") ? "7" : "all") : "all";
  const page = Math.max(1, Number(params.get("processingPage")) || 1);
  const [data, setData] = useState<{ posts: RecoveryPost[]; total: number }>();
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [query, setQuery] = useState(params.get("postId") ?? "");
  const search = params.get("postId") ?? "";
  useEffect(() => {
    setParams(p => { const n = new URLSearchParams(p); n.delete("processingPage"); return n; }, { replace: true });
    window.scrollTo(0, 0);
  }, []);
  useEffect(() => {
    let alive = true; setData(undefined); setError("");
    const q = new URLSearchParams({ category, page: String(page), limit: "10" });
    if (search) q.set("postId", search);
    if (days !== "all") {
      const since = new Date();
      if (days === "today") since.setHours(0, 0, 0, 0); else since.setDate(since.getDate() - Number(days));
      q.set("failedSince", since.toISOString());
    }
    void api<{ posts: RecoveryPost[]; total: number }>(`/post-processing?${q}`).then(r => { if (alive) setData(r); }).catch(e => { if (alive) setError(e.message); });
    return () => { alive = false; };
  }, [category, days, page, search, revision]);
  const change = (key: string, value: string) => setParams(p => { const n = new URLSearchParams(p); n.set(key, value); n.delete("processingPage"); return n; });
  return <Stack spacing={2}>
    <Stack direction={{ xs: "column", sm: "row" }} spacing={1} component="form" onSubmit={e => { e.preventDefault(); change("postId", query.trim()); }}>
      {periodFilter && <TextField select size="small" label="실패 발생 기간" value={days} onChange={e => change("days", e.target.value)} sx={{ minWidth: 160 }}>
        <MenuItem value="today">오늘</MenuItem><MenuItem value="7">최근 7일</MenuItem><MenuItem value="30">최근 30일</MenuItem><MenuItem value="all">전체 기간</MenuItem>
      </TextField>}
      <TextField size="small" label="게시글 번호" value={query} onChange={e => setQuery(e.target.value)} slotProps={{ htmlInput: { inputMode: "numeric", pattern: "[0-9]*" } }} />
      <Button type="submit">검색</Button><Button onClick={() => setRevision(x => x + 1)}>새로고침</Button>
    </Stack>
    <Typography variant="body2" color="text.secondary">{category === "INITIAL_FAILED" ? "사용자 저장 후 자동 재시도가 종료된 최초 실패입니다. 관리자 재시도 실패는 별도 탭에서 확인합니다." : category === "UNPROCESSABLE" ? "운영자가 사유를 남겨 처리 불가로 분류한 게시글입니다." : "게시글을 누르면 원인과 전체 처리 흐름을 자세히 볼 수 있습니다."} {days !== "all" && "실제 실패 시각을 기준으로 조회합니다."}</Typography>
    {error ? <Alert severity="error">조회 실패: {error}</Alert> : !data ? <CircularProgress size={28} aria-label="게시글 목록 불러오는 중" /> : <>
      <Typography variant="subtitle2">{data.total.toLocaleString()}개 게시글 · {page}페이지</Typography>
      {data.posts.length === 0 && <Alert severity="info">이 조건에 해당하는 게시글이 없습니다.{days !== "all" && " 기간을 넓혀 보세요."}</Alert>}
      {data.posts.map(post => <Card variant="outlined" key={post.postId}><CardActionArea component={RouterLink} to={`/posts/${post.postId}/processing?category=${category}&days=${days}`} aria-label={`${post.title ?? `게시글 ${post.postId}`} 처리 상세`}><CardContent>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={1} sx={{ justifyContent: "space-between" }}><Box><Typography variant="subtitle1">{post.title ?? "제목 없음"}</Typography><Typography variant="caption" color="text.secondary">게시글 #{post.postId}{failedJobs(post).length > 0 ? ` · 최근 실패 ${date(lastFailure(post))}` : ""}</Typography></Box><Chip size="small" label={state(post)} color={post.disposition === "UNPROCESSABLE" ? "default" : failedJobs(post).length ? "error" : "info"} /></Stack>
        <Typography variant="body2" sx={{ mt: 1 }}>{overview(post)}</Typography>
        <Typography variant="caption" color="text.secondary">전체 {post.jobs.length}건 · 완료 {post.jobs.filter(j => j.status === "COMPLETED").length}건 · 실패 {failedJobs(post).length}건 · 상세 보기 →</Typography>
      </CardContent></CardActionArea></Card>)}
      {data.total > 10 && <Pagination count={Math.ceil(data.total / 10)} page={page} onChange={(_, p) => { setParams(n => { const q = new URLSearchParams(n); q.set("processingPage", String(p)); return q; }); window.scrollTo(0, 0); }} />}
    </>}
  </Stack>;
}

function overview(post: RecoveryPost) {
  if (post.disposition === "UNPROCESSABLE") return post.dispositionReason ?? "운영자가 처리 불가로 분류했습니다.";
  const groups = [...new Set(failedJobs(post).map(j => `${recoveryStageNames[j.failureStage ?? j.type] ?? j.type}: ${j.failure?.summary ?? j.failureReason ?? "실패 사유 미기록"}`))];
  return groups.length ? `${groups.slice(0, 2).join(" · ")}${groups.length > 2 ? ` 외 ${groups.length - 2}개 원인` : ""}` : active(post) ? "작업을 처리하고 있습니다." : "모든 작업이 완료되었습니다.";
}

export function PostProcessingDetail() {
  const { postId = "" } = useParams();
  return <ProcessingDetail key={postId} postId={postId} />;
}

function ProcessingDetail({ postId }: { postId: string }) {
  const [params] = useSearchParams();
  const location = useLocation();
  const [post, setPost] = useState<RecoveryPost>();
  const [pipeline, setPipeline] = useState<PipelineResponse>();
  const [error, setError] = useState("");
  const [traceError, setTraceError] = useState("");
  const [revision, setRevision] = useState(0);
  const [checked, setChecked] = useState<Date>();
  useEffect(() => { window.scrollTo(0, 0); }, [location.pathname]);
  useEffect(() => {
    let alive = true; let timer: ReturnType<typeof setTimeout>;
    const refresh = async () => {
      let poll = true;
      const [recovery, flow] = await Promise.allSettled([api<RecoveryPost>(`/parsing-posts/${postId}`), api<PipelineResponse>(`/parsing-pipeline?postId=${postId}`)]);
      if (!alive) return;
      if (recovery.status === "fulfilled") { setPost(recovery.value); setError(""); setChecked(new Date()); poll = active(recovery.value); } else setError(recovery.reason.message);
      if (flow.status === "fulfilled") { setPipeline(flow.value); setTraceError(""); } else setTraceError(flow.reason.message);
      if (poll) timer = setTimeout(refresh, 5000);
    };
    void refresh(); return () => { alive = false; clearTimeout(timer); };
  }, [postId, revision]);
  const back = new URLSearchParams({ category: params.get("category") ?? "INITIAL_FAILED", days: params.get("days") ?? "7" });
  return <Stack spacing={2.5}>
    <Stack direction="row" spacing={1}><Button component={RouterLink} to={`/posts?${back}`} onClick={() => window.scrollTo(0, 0)}>← 게시글 목록</Button><Button component={RouterLink} to={`/posts/${postId}/show`}>게시글 정보</Button></Stack>
    <Box><Typography variant="overline" color="text.secondary">게시글 #{postId} · 처리 상세</Typography><Typography variant="h4">{post?.title ?? pipeline?.execution?.title ?? `게시글 #${postId}`}</Typography></Box>
    {error && <Alert severity="error">현재 상태 조회 실패: {error}{post && " · 마지막 확인 결과를 표시합니다."}</Alert>}
    {!post && !error && <CircularProgress size={28} />}
    {post && <>
      <Card variant="outlined"><CardContent><Stack spacing={1.5}>
        <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}><Chip label={state(post)} color={post.disposition === "UNPROCESSABLE" ? "default" : active(post) ? "info" : failedJobs(post).length ? "error" : "success"} /><Typography>전체 {post.jobs.length}건 · 완료 {post.jobs.filter(j => j.status === "COMPLETED").length}건 · 실패 {failedJobs(post).length}건</Typography></Stack>
        <Typography>{overview(post)}</Typography>
        {post.dispositionChangedAt && <Typography variant="caption">운영 상태 변경: {date(post.dispositionChangedAt)} · {post.dispositionReason}</Typography>}
        <Typography variant="caption" color="text.secondary">{checked && `마지막 확인 ${checked.toLocaleTimeString("ko-KR")}`} {active(post) ? "· 처리 중에는 5초마다 자동 갱신합니다." : "· 현재 실행이 종료되었습니다."}</Typography>
        <ProcessingActions post={post} onUpdated={r => { setPost(r); setRevision(v => v + 1); }} />
        <Button sx={{ alignSelf: "flex-start" }} onClick={() => setRevision(v => v + 1)}>상태 새로고침</Button>
      </Stack></CardContent></Card>
      <Stack direction={{ xs: "column", md: "row" }} spacing={1}>
        {Object.entries(recoveryTypeNames).map(([type, label]) => { const jobs = post.jobs.filter(j => j.type === type); return <Box key={type} sx={{ flex: 1, p: 1.5, border: 1, borderColor: jobs.some(j => j.status === "FAILED") ? "error.main" : "divider", borderRadius: 1 }}><Typography variant="subtitle2">{label}</Typography><Typography variant="body2">{!jobs.length ? "생성된 작업 없음" : jobs.some(j => j.status === "PROCESSING") ? "처리 중" : jobs.some(j => j.status === "PENDING") ? "대기 중" : jobs.some(j => j.status === "FAILED") ? `실패 ${jobs.filter(j => j.status === "FAILED").length}건` : "완료"}</Typography></Box>; })}
      </Stack>
      {failedJobs(post).length > 0 && <Box><Typography variant="h6" sx={{ mb: 1 }}>실패 원인</Typography><Stack spacing={1}>{failedJobs(post).map(job => <FailureCard key={`${job.type}-${job.id}`} job={job} />)}</Stack></Box>}
      <Accordion variant="outlined"><AccordionSummary expandIcon={<ExpandMoreIcon />}>전체 작업 상태 · {post.jobs.length}건</AccordionSummary><AccordionDetails><Stack spacing={1}>{post.jobs.map(j => <Box key={`${j.type}-${j.id}`}><Typography variant="subtitle2">{recoveryTypeNames[j.type]} · {recoveryLabel(j)}</Typography><Typography variant="body2">총 {j.attempts}회 실행 · 작업 #{j.id}{j.nextAttemptAt ? ` · 다음 실행 ${date(j.nextAttemptAt)}` : ""}</Typography></Box>)}</Stack></AccordionDetails></Accordion>
    </>}
    {traceError && <Alert severity="warning">처리 이력 조회 실패: {traceError}</Alert>}
    {pipeline?.execution && <ProcessingTimeline traces={pipeline.execution.traces} nodes={pipeline.nodes} />}
  </Stack>;
}

function FailureCard({ job }: { job: RecoveryJob }) {
  return <Card variant="outlined"><CardContent><Stack spacing={1}>
    <Typography variant="subtitle1">{recoveryStageNames[job.failureStage ?? job.type] ?? job.type} · {recoveryLabel(job)}</Typography>
    <Typography>{job.failure?.summary ?? job.failureReason ?? "저장된 실패 사유가 없습니다."}</Typography>
    <Typography variant="body2" sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{job.failure?.detail ?? job.failureReason}</Typography>
    <Typography variant="caption" color="text.secondary">{job.failure?.code && `오류 코드 ${job.failure.code} · `}{date(job.lastFailedAt)} · 총 {job.attempts}회 실행 · 작업 #{job.id}</Typography>
  </Stack></CardContent></Card>;
}

function ProcessingActions({ post, onUpdated }: { post: RecoveryPost; onUpdated: (r: RecoveryPost) => void }) {
  const [action, setAction] = useState<"retry" | "UNPROCESSABLE" | "OPEN">();
  const [reason, setReason] = useState(""); const [saving, setSaving] = useState(false); const [error, setError] = useState("");
  const choose = (a: typeof action) => { setAction(a); setReason(""); setError(""); };
  const submit = async () => {
    if (saving || !action || !reason.trim()) return;
    setSaving(true); setError("");
    try {
      const result = await api<RecoveryPost>(action === "retry" ? `/parsing-posts/${post.postId}/retry` : `/post-processing/${post.postId}/disposition`, { method: action === "retry" ? "POST" : "PATCH", body: JSON.stringify({ reason, ...(action === "retry" ? {} : { disposition: action }) }) });
      onUpdated(result); setAction(undefined);
    } catch (e) { setError(e instanceof Error ? e.message : "변경 실패"); } finally { setSaving(false); }
  };
  const label = action === "retry" ? "실패 단계 재시도" : action === "UNPROCESSABLE" ? "처리 불가로 변경" : "복구 대상으로 되돌리기";
  return <>
    <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
      {post.disposition === "UNPROCESSABLE" ? <Button variant="outlined" disabled={active(post)} onClick={() => choose("OPEN")}>복구 대상으로 되돌리기</Button> : failedJobs(post).length > 0 && <><Button variant="contained" disabled={active(post)} onClick={() => choose("retry")}>실패 단계 {failedJobs(post).length}건 재시도</Button><Button disabled={active(post)} onClick={() => choose("UNPROCESSABLE")}>처리 불가로 변경</Button></>}
    </Stack>
    <Dialog open={!!action} onClose={() => { if (!saving) setAction(undefined); }} fullWidth maxWidth="sm"><DialogTitle>{label}</DialogTitle><DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
      <Typography>게시글 #{post.postId} · {post.title ?? "제목 없음"}</Typography>
      <Typography>{action === "retry" ? `실패 ${failedJobs(post).length}건을 다시 실행합니다. 완료된 단계는 유지합니다.` : action === "UNPROCESSABLE" ? "실패 이력을 보존하고 처리 불가 목록으로 옮깁니다. 복구 대상으로 되돌리기 전에는 재시도할 수 없습니다." : "실패 목록에서 다시 확인할 수 있게 합니다. 이 동작만으로 재시도를 실행하지 않습니다."}</Typography>
      <TextField label="변경 사유" multiline minRows={3} value={reason} disabled={saving} onChange={e => setReason(e.target.value)} slotProps={{ htmlInput: { maxLength: 500 } }} />
      {error && <Alert severity="error">{error}</Alert>}
    </Stack></DialogContent><DialogActions><Button disabled={saving} onClick={() => setAction(undefined)}>취소</Button><Button variant="contained" disabled={saving || !reason.trim()} onClick={submit}>{saving ? "처리 중…" : label}</Button></DialogActions></Dialog>
  </>;
}
