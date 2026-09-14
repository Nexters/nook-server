import { Alert, Box, Button, Chip, CircularProgress, Stack, Typography } from "@mui/material";
import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { api } from "./api";
import { recoveryLabel, recoveryTypeNames, recoveryStageNames, type RecoveryJob } from "./ParsingRecoveryTracking";

export type RecoveryPost = { postId: number; jobs: RecoveryJob[]; title?: string; disposition?: string; dispositionReason?: string; dispositionChangedAt?: string };
export const failedJobs = (post: RecoveryPost) => post.jobs.filter(job => job.status === "FAILED");
const active = (post: RecoveryPost) => post.jobs.some(job => job.status === "PENDING" || job.status === "PROCESSING");
const dateText = (value: string) => new Date(value).toLocaleString("ko-KR");
export function PostRecoveryDetails({ post, onRetryJob }: { post: RecoveryPost; onRetryJob?: (job: RecoveryJob) => void }) {
  return <Stack spacing={1}>
    <Typography component={RouterLink} to={`/posts/${post.postId}/show`} sx={{ color: "primary.main", p: .5, "&:hover": { bgcolor: "action.hover" } }}>게시글 #{post.postId}</Typography>
    <Typography variant="body2">전체 {post.jobs.length}건 · 실패 {failedJobs(post).length}건 · 완료 {post.jobs.filter(job => job.status === "COMPLETED").length}건 · 대기/처리 중 {post.jobs.filter(job => ["PENDING", "PROCESSING"].includes(job.status)).length}건</Typography>
    {post.jobs.map(job => <Box key={`${job.type}-${job.id}`} sx={{ p: 1.5, bgcolor: "action.hover", borderRadius: 1 }}>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={1} sx={{ alignItems: { sm: "center" }, justifyContent: "space-between" }}>
        <Typography variant="subtitle2">{recoveryTypeNames[job.type] ?? job.type} <Typography component="span" variant="caption" color="text.secondary">작업 #{job.id} · {job.attempts}회 실행</Typography></Typography>
        <Stack direction="row" spacing={1}><Chip size="small" label={recoveryLabel(job)} color={job.status === "FAILED" ? "error" : job.status === "COMPLETED" ? "success" : "default"} />
          {onRetryJob && job.status === "FAILED" && !["POST_CONTENT", "PLACE_PARSING"].includes(job.type) && <Button size="small" onClick={() => onRetryJob(job)}>이 작업 재시도</Button>}
        </Stack>
      </Stack>
      {(job.lastFailedAt || job.failureReason || job.status === "FAILED") && <Typography variant="caption" color="text.secondary">마지막 실패: {job.lastFailedAt ? dateText(job.lastFailedAt) : "기록 없음 (과거 작업)"}{job.failureStage ? ` · ${recoveryStageNames[job.failureStage] ?? job.failureStage}` : ""}</Typography>}
      {job.failureReason && <Typography variant="body2" sx={{ overflowWrap: "anywhere" }}>최근 실패: {job.failureReason}</Typography>}
      {job.nextAttemptAt && <Typography variant="caption">다음 실행 가능 시각: {dateText(job.nextAttemptAt)}</Typography>}
    </Box>)}
  </Stack>;
}

export function PostRecoveryTracking({ postId, onUpdate, onClose }: { postId: number; onUpdate: () => void; onClose: () => void }) {
  const [post, setPost] = useState<RecoveryPost>();
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [checkedAt, setCheckedAt] = useState<Date>();
  useEffect(() => {
    let alive = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let previous = "";
    const refresh = async () => {
      let poll = true;
      try {
        const result = await api<RecoveryPost>(`/parsing-posts/${postId}`);
        if (!alive) return;
        setPost(result); setError(""); setCheckedAt(new Date()); poll = active(result);
        const signature = JSON.stringify(result.jobs.map(job => [job.type, job.id, job.status, job.attempts]));
        if (signature !== previous) { previous = signature; onUpdate(); }
      } catch (cause) { if (alive) setError(cause instanceof Error ? cause.message : "상태 조회 실패"); }
      finally { if (alive && poll) timer = setTimeout(refresh, 5000); }
    };
    void refresh();
    return () => { alive = false; if (timer) clearTimeout(timer); };
  }, [postId, revision, onUpdate]);
  return <Box sx={{ border: 1, borderColor: "primary.main", borderRadius: 1, p: 2 }}><Stack spacing={1.5}>
    <Stack direction="row" sx={{ justifyContent: "space-between" }}><Typography variant="h6">게시물 복구 진행</Typography><Button onClick={onClose}>닫기</Button></Stack>
    {error && <Alert severity="error">상태 조회 실패: {error} {post ? "마지막 확인 결과를 표시합니다." : "자동으로 다시 확인합니다."}</Alert>}
    {!post && !error && <CircularProgress size={24} />}
    {post && <><Alert severity={active(post) ? "info" : failedJobs(post).length ? "warning" : "success"}>{active(post) ? "처리 상태를 5초마다 확인합니다." : failedJobs(post).length ? `실패한 작업 ${failedJobs(post).length}건이 남아 있습니다.` : post.jobs.some(job => job.recoveryStatus === "RECOVERED") ? "실패했던 작업이 복구되어 모든 단계가 완료됐습니다." : "모든 단계가 완료됐습니다."}</Alert><PostRecoveryDetails post={post} /></>}
    <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}>{checkedAt && <Typography variant="caption">마지막 확인: {checkedAt.toLocaleTimeString("ko-KR")}</Typography>}<Button onClick={() => setRevision(value => value + 1)}>상태 다시 확인</Button></Stack>
  </Stack></Box>;
}
