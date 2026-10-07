import { Alert, Box, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField, Typography } from "@mui/material";
import { useEffect, useRef, useState } from "react";

type Outcome = "PENDING" | "UPDATED" | "PRESERVED" | "NO_DATA" | "NO_SOURCE" | "FAILED";
type Job = { placeId: number; status: "PENDING" | "PROCESSING" | "COMPLETED" | "PARTIAL" | "FAILED"; attempt: number; photos: Outcome; tags: Outcome; updatedAt: string };
type Result = { job: Job | null };
const names: Record<Job["status"], string> = { PENDING: "재처리 대기", PROCESSING: "재처리 중", COMPLETED: "재처리 완료", PARTIAL: "일부 정보 미확보", FAILED: "재처리 실패" };
const outcomes: Record<Outcome, string> = { PENDING: "대기", UPDATED: "갱신 완료", PRESERVED: "수정된 값 보존", NO_DATA: "정보를 찾지 못함", NO_SOURCE: "추출할 게시물 없음", FAILED: "처리 실패" };
const active = (job: Job | null | undefined) => job?.status === "PENDING" || job?.status === "PROCESSING";
const apiBase = (import.meta.env.VITE_ADMIN_API_BASE_URL ?? "").replace(/\/$/, "");
async function request(placeId: number, init?: RequestInit): Promise<Result> {
  const response = await fetch(`${apiBase}/api/admin/v1/places/${placeId}/reprocessing`, { credentials: "include", ...init, headers: { "Content-Type": "application/json" } });
  const body = await response.json().catch(() => ({}));
  if (!response.ok || !body.success) throw new Error(body.error?.reason ?? "재처리 요청을 확인하지 못했습니다.");
  return body.success;
}

export function PlaceReprocessing({ placeId, name, affectedUserCount, linkedPostCount, onComplete }: { placeId: number; name: string; affectedUserCount: number; linkedPostCount: number; onComplete: () => void }) {
  const [job, setJob] = useState<Job | null>();
  const [loadError, setLoadError] = useState("");
  const [saveError, setSaveError] = useState("");
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [saving, setSaving] = useState(false);
  const [revision, setRevision] = useState(0);
  const callback = useRef(onComplete);
  callback.current = onComplete;
  const wasActive = useRef(false);
  useEffect(() => {
    let disposed = false;
    let timer: ReturnType<typeof setTimeout>;
    const poll = async () => {
      try {
        const result = await request(placeId);
        if (disposed) return;
        setJob(result.job); setLoadError("");
        if (wasActive.current && !active(result.job)) callback.current();
        wasActive.current = active(result.job);
        if (active(result.job)) timer = setTimeout(poll, 5000);
      } catch (cause) {
        if (!disposed) setLoadError(cause instanceof Error ? cause.message : "상태 조회에 실패했습니다.");
      }
    };
    void poll();
    return () => { disposed = true; clearTimeout(timer); };
  }, [placeId, revision]);
  const submit = async () => {
    setSaving(true); setSaveError("");
    try {
      const result = await request(placeId, { method: "POST", body: JSON.stringify({ reason }) });
      setJob(result.job); wasActive.current = true; setOpen(false); setReason(""); setRevision(value => value + 1);
    } catch (cause) { setSaveError(cause instanceof Error ? cause.message : "재처리 예약에 실패했습니다."); }
    finally { setSaving(false); }
  };
  return <Box><Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ alignItems: { sm: "center" }, justifyContent: "space-between" }}>
    <Box><Typography variant="h6">사진·태그 재처리</Typography><Typography variant="body2" color="text.secondary">이 장소의 정보만 다시 수집합니다. 수동으로 수정한 항목은 보존합니다.</Typography></Box>
    <Button variant="outlined" disabled={saving || job === undefined || !!loadError || active(job)} onClick={() => { setOpen(true); setSaveError(""); }}>사진·태그 재처리</Button>
  </Stack>
    {job === undefined && !loadError && <CircularProgress size={20} aria-label="재처리 상태 불러오는 중" sx={{ mt: 2 }} />}
    {loadError && <Alert severity="error" sx={{ mt: 2 }} action={<Button color="inherit" onClick={() => setRevision(value => value + 1)}>다시 조회</Button>}>{loadError}</Alert>}
    {job === null && <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>아직 재처리한 이력이 없습니다.</Typography>}
    {job && <Alert severity={active(job) ? "info" : job.status === "COMPLETED" ? "success" : "warning"} sx={{ mt: 2 }}>
      <Typography variant="subtitle2">{names[job.status]}</Typography>
      <Typography variant="body2">사진: {outcomes[job.photos]} · 태그: {outcomes[job.tags]}</Typography>
      <Typography variant="body2">{active(job) ? "처리 상태를 5초마다 확인합니다." : `마지막 변경: ${new Date(job.updatedAt).toLocaleString("ko-KR")}`}</Typography>
    </Alert>}
    <Dialog open={open} onClose={() => { if (!saving) setOpen(false); }} fullWidth maxWidth="sm">
      <DialogTitle>사진·태그 재처리</DialogTitle><DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
        <Typography>{name}의 사진·태그를 다시 수집합니다.</Typography>
        <Alert severity="info">연결 게시글 {linkedPostCount}개, 저장 사용자 {affectedUserCount}명의 화면에 갱신된 공용 정보가 표시됩니다. 외부 서비스 호출 비용이 발생할 수 있습니다.</Alert>
        <Typography variant="body2">수동 수정값은 보존합니다. 태그는 연결 게시물의 본문을 근거로 추출하며, 근거가 없으면 별도로 안내합니다.</Typography>
        <TextField label="재처리 사유" required multiline minRows={2} value={reason} onChange={event => setReason(event.target.value)} slotProps={{ htmlInput: { maxLength: 500 } }} />
        {saveError && <Alert severity="error">{saveError}</Alert>}
      </Stack></DialogContent><DialogActions><Button disabled={saving} onClick={() => setOpen(false)}>취소</Button><Button variant="contained" disabled={saving || !reason.trim()} onClick={submit}>{saving ? "예약 중…" : "재처리 예약"}</Button></DialogActions>
    </Dialog>
  </Box>;
}
