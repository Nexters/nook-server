import { Alert, Box, Button, Chip, CircularProgress, Stack, Typography } from "@mui/material";
import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { api } from "./api";

export type RecoveryJob = { id: number; postId: number; type: string; status: string; attempts: number; failureReason?: string; nextAttemptAt?: string; updatedAt: string; recoveryStatus?: string; outcome?: string; lastFailedAt?: string; failureStage?: string; failure?: { summary: string; detail: string; code?: string } };
export const recoveryTypeNames: Record<string, string> = { POST_CONTENT: "본문 파싱", PLACE_PARSING: "장소 파싱", POST_MEDIA: "미디어 저장", PLACE_THUMBNAILS: "썸네일", PLACE_TAGS: "장소 태그" };
export const recoveryStageNames: Record<string, string> = {
  ...recoveryTypeNames, CONTENT_FETCH: "원문 가져오기", CONTENT_COVER_TITLE: "커버·제목 추출",
  CONTENT_INFERENCE: "본문 분석", CONTENT_SAVE: "본문 저장", PLACE_TEXT_CLUES: "장소 단서 추출",
  PLACE_TEXT_RESOLUTION: "장소 검색", PLACE_IMAGE_OCR: "이미지 OCR", PLACE_IMAGE_CLUES: "이미지 장소 추출",
  PLACE_IMAGE_RESOLUTION: "이미지 장소 검색", TITLE_FINALIZATION: "제목 생성", PLACE_SAVE: "장소 저장",
};
export const recoveryStatusNames: Record<string, string> = { PENDING: "대기", PROCESSING: "처리 중", COMPLETED: "완료", FAILED: "실패" };
const retryStatusNames: Record<string, string> = { RETRY_PENDING: "재시도 대기", RETRY_PROCESSING: "재시도 처리 중", RECOVERED: "재시도 후 복구 완료", RETRY_FAILED: "재시도 실패" };
export const recoveryLabel = (job: RecoveryJob) => retryStatusNames[job.recoveryStatus ?? ""] ?? recoveryStatusNames[job.status] ?? job.status;

export function ParsingRecoveryTracking({ jobId, onUpdate, onClose }: { jobId: number; onUpdate: () => void; onClose: () => void }) {
  const [job, setJob] = useState<RecoveryJob>();
  const [error, setError] = useState("");
  const [checkedAt, setCheckedAt] = useState<Date>();
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let previousStatus: string | undefined;
    const refresh = async () => {
      let shouldPoll = true;
      try {
        const next = await api<RecoveryJob>(`/parsing-jobs/${jobId}`);
        if (!active) return;
        setJob(next); setCheckedAt(new Date()); setError("");
        shouldPoll = next.status === "PENDING" || next.status === "PROCESSING";
        if (next.status !== previousStatus) { onUpdate(); previousStatus = next.status; }
      } catch (cause) {
        if (active) setError(cause instanceof Error ? cause.message : "상태를 확인하지 못했습니다.");
      } finally {
        if (active && shouldPoll) timer = setTimeout(refresh, 5000);
      }
    };
    void refresh();
    return () => { active = false; if (timer) clearTimeout(timer); };
  }, [jobId, revision, onUpdate]);
  return <Box sx={{ border: 1, borderColor: "primary.main", borderRadius: 1, p: 2 }}>
    <Stack spacing={1.5}>
      <Stack direction="row" sx={{ alignItems: "center", justifyContent: "space-between" }}>
        <Typography variant="subtitle1">재시도 작업 확인 · #{jobId}</Typography>
        <Button size="small" onClick={onClose}>닫기</Button>
      </Stack>
      {error && <Alert severity="error">상태 조회 실패: {error} {job ? "표시된 상태는 마지막 확인 결과입니다." : "자동으로 다시 확인합니다."}</Alert>}
      {!job && !error && <CircularProgress size={24} aria-label="재시도 상태 불러오는 중" />}
      {job && <>
        <Typography component={RouterLink} to={`/posts/${job.postId}/show`} sx={{ color: "primary.main" }}>게시글 #{job.postId} · {recoveryTypeNames[job.type] ?? job.type}</Typography>
        <Chip sx={{ alignSelf: "flex-start" }} label={recoveryLabel(job)} color={job.status === "COMPLETED" ? "success" : job.status === "FAILED" ? "error" : "info"} />
        <Typography variant="body2">{job.status === "COMPLETED" ? (job.recoveryStatus === "RECOVERED" ? "이전에 실패한 작업이 재시도를 거쳐 완료됐습니다." : "작업이 완료됐습니다.") : job.status === "FAILED" ? (job.recoveryStatus === "RETRY_FAILED" ? "재시도 후에도 실패했습니다. 실패 목록에서 원인을 확인하고 다시 시도할 수 있습니다." : "작업이 실패했습니다. 실패 목록에서 원인을 확인할 수 있습니다.") : "처리 상태를 5초마다 자동으로 확인합니다."}</Typography>
        {job.status !== "COMPLETED" && job.failureReason && <Typography variant="body2" sx={{ overflowWrap: "anywhere" }}>최근 실패: {job.failureReason}</Typography>}
        {job.nextAttemptAt && <Typography variant="caption">실행 가능 시각: {new Date(job.nextAttemptAt).toLocaleString("ko-KR")}</Typography>}
      </>}
      <Stack direction="row" sx={{ alignItems: "center", flexWrap: "wrap", gap: 1 }}>
        {checkedAt && <Typography variant="caption" color="text.secondary">마지막 확인: {checkedAt.toLocaleTimeString("ko-KR")}</Typography>}
        <Button size="small" onClick={() => setRevision(value => value + 1)}>상태 다시 확인</Button>
      </Stack>
    </Stack>
  </Box>;
}
