import { Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField, Typography } from "@mui/material";
import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { api } from "./api";

type Job = { id: number; postId: number; type: string; status: string; attempts: number; failureReason?: string; nextAttemptAt?: string; updatedAt: string };
type Page = { jobs: Job[]; hasNext: boolean };
const typeNames: Record<string, string> = { POST_MEDIA: "미디어 저장", PLACE_THUMBNAILS: "썸네일", PLACE_TAGS: "장소 태그" };
const statusNames: Record<string, string> = { PENDING: "대기", PROCESSING: "처리 중", COMPLETED: "완료", FAILED: "실패" };
const dateText = (value: string) => new Date(value).toLocaleString("ko-KR");

export function ParsingRecovery({ postId }: { postId?: string }) {
  const [status, setStatus] = useState(postId ? "" : "FAILED");
  const [beforeId, setBeforeId] = useState<number>();
  const [page, setPage] = useState<Page>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [selected, setSelected] = useState<Job>();
  const [reason, setReason] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");
  const [notice, setNotice] = useState("");
  useEffect(() => { setBeforeId(undefined); setStatus(postId ? "" : "FAILED"); }, [postId]);
  useEffect(() => {
    let active = true;
    setLoading(true); setError(""); setPage(undefined);
    const query = new URLSearchParams({ limit: "20" });
    if (postId) query.set("postId", postId);
    if (status) query.set("status", status);
    if (beforeId) query.set("beforeId", String(beforeId));
    api<Page>(`/parsing-jobs?${query}`).then(value => { if (active) setPage(value); })
      .catch((cause: unknown) => { if (active) setError(cause instanceof Error ? cause.message : "작업을 불러오지 못했습니다."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [postId, status, beforeId, revision]);
  const retry = async () => {
    if (!selected || saving) return;
    setSaving(true); setSaveError("");
    try {
      await api(`/parsing-jobs/${selected.id}/retry`, { method: "POST", body: JSON.stringify({ reason }) });
      setNotice(`게시글 #${selected.postId}의 ${typeNames[selected.type] ?? selected.type} 재시도를 예약했습니다.`);
      setSelected(undefined); setReason(""); setRevision(value => value + 1);
    } catch (cause) { setSaveError(cause instanceof Error ? cause.message : "재시도 요청에 실패했습니다."); }
    finally { setSaving(false); }
  };
  return <Card variant="outlined"><CardContent><Stack spacing={2}>
    <Stack direction={{ xs: "column", lg: "row" }} spacing={1} sx={{ justifyContent: "space-between" }}>
      <Box><Typography variant="h6">후속 작업 복구</Typography><Typography variant="body2" color="text.secondary">실패한 미디어·썸네일·태그 작업을 확인하고 해당 단계만 다시 처리합니다.</Typography></Box>
      <Stack direction="row" spacing={1}>
        <TextField select size="small" label="작업 상태" value={status} sx={{ minWidth: 130 }} onChange={event => { setStatus(event.target.value); setBeforeId(undefined); }}>
          <MenuItem value="">전체</MenuItem>{Object.entries(statusNames).map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}
        </TextField>
        <Button sx={{ whiteSpace: "nowrap", flexShrink: 0 }} onClick={() => setRevision(value => value + 1)} disabled={loading}>새로고침</Button>
      </Stack>
    </Stack>
    {notice && <Alert severity="success" onClose={() => setNotice("")}>{notice}</Alert>}
    {error && <Alert severity="error">{error}</Alert>}
    {loading ? <Box sx={{ p: 2 }}><CircularProgress size={24} aria-label="후속 작업 불러오는 중" /></Box> : page?.jobs.length === 0 ? <Alert severity="info">이 조건에 해당하는 작업이 없습니다.</Alert> : page?.jobs.map(job => <Box key={job.id} sx={{ borderTop: 1, borderColor: "divider", pt: 2 }}>
      <Stack direction={{ xs: "column", lg: "row" }} spacing={1} sx={{ justifyContent: "space-between", alignItems: { lg: "center" } }}>
        <Stack spacing={.5} component={RouterLink} to={`/posts/${job.postId}/show`} sx={{ flex: 1, color: "inherit", textDecoration: "none", p: .5, "&:hover": { bgcolor: "action.hover" }, "&:focus-visible": { outline: "2px solid", outlineColor: "primary.main" } }}>
          <Typography sx={{ color: "primary.main" }}>게시글 #{job.postId} · {typeNames[job.type] ?? job.type}</Typography>
          <Typography variant="caption" color="text.secondary">작업 #{job.id} · 총 {job.attempts}회 실행 · {dateText(job.updatedAt)}</Typography>
        </Stack>
        <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}><Chip size="small" label={statusNames[job.status] ?? job.status} color={job.status === "FAILED" ? "error" : job.status === "COMPLETED" ? "success" : "default"} />
          {job.status === "FAILED" && <Button variant="outlined" size="small" onClick={() => { setSelected(job); setReason(""); setSaveError(""); }}>이 단계 재시도</Button>}
        </Stack>
      </Stack>
      {job.failureReason && <Typography variant="body2" sx={{ mt: 1, overflowWrap: "anywhere" }}>최근 실패: {job.failureReason}</Typography>}
      {job.nextAttemptAt && <Typography variant="caption" color="text.secondary">실행 가능 시각: {dateText(job.nextAttemptAt)}</Typography>}
    </Box>)}
    <Stack direction="row" spacing={1}>{beforeId && <Button onClick={() => setBeforeId(undefined)}>처음으로</Button>}{page?.hasNext && <Button disabled={loading} onClick={() => setBeforeId(page.jobs.at(-1)?.id)}>다음 작업</Button>}</Stack>
  </Stack></CardContent>
    <Dialog open={!!selected} onClose={() => { if (!saving) setSelected(undefined); }} fullWidth maxWidth="sm">
      <DialogTitle>실패한 단계 재시도</DialogTitle><DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
        <Typography>게시글 #{selected?.postId}의 {typeNames[selected?.type ?? ""]} 작업만 다시 실행합니다. 다른 단계는 다시 실행하지 않으며 외부 서비스 호출 비용이 발생할 수 있습니다.</Typography>
        <TextField label="재처리 사유" multiline minRows={2} value={reason} onChange={event => setReason(event.target.value)} slotProps={{ htmlInput: { maxLength: 500 } }} disabled={saving} />
        {saveError && <Alert severity="error">{saveError}</Alert>}
      </Stack></DialogContent><DialogActions><Button disabled={saving} onClick={() => setSelected(undefined)}>취소</Button><Button variant="contained" disabled={saving || !reason.trim()} onClick={retry}>{saving ? "예약 중…" : "재시도 예약"}</Button></DialogActions>
    </Dialog>
  </Card>;
}
