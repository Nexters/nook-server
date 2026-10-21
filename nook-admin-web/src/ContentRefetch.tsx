import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField, Typography } from "@mui/material";
import { useState } from "react";
import { api } from "./api";
import type { RecoveryPost } from "./ParsingPostRecovery";

export function ContentRefetch({ post, onScheduled }: { post: RecoveryPost; onScheduled?: () => void }) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [scheduled, setScheduled] = useState(false);
  const active = post.jobs.some(job => ["PENDING", "PROCESSING"].includes(job.status));
  const submit = async () => {
    if (saving || !reason.trim()) return;
    setSaving(true); setError("");
    try {
      await api<RecoveryPost>(`/parsing-posts/${post.postId}/refetch-content`, { method: "POST", body: JSON.stringify({ reason }) });
      setScheduled(true); setOpen(false); onScheduled?.();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "재수집 예약 실패");
    } finally { setSaving(false); }
  };
  return <>
    <Button variant="outlined" disabled={saving || active || scheduled || post.disposition === "UNPROCESSABLE" || !post.jobs.some(job => job.type === "POST_CONTENT")} onClick={() => { setOpen(true); setReason(""); setError(""); }}>원문 새로 수집</Button>
    {scheduled && <Alert severity="success">재수집을 예약했습니다. 아래 처리 상태에서 진행·결과를 확인하세요.</Alert>}
    <Dialog open={open} onClose={() => { if (!saving) setOpen(false); }} fullWidth maxWidth="sm">
      <DialogTitle>원문 새로 수집</DialogTitle>
      <DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
        <Typography>게시글 #{post.postId}의 수집 캐시를 초기화하고 Instagram 원문과 장소를 다시 처리합니다. 완료된 게시물도 다시 수집하며 외부 서비스 호출 비용이 발생할 수 있습니다.</Typography>
        <Typography variant="body2">새 수집이 성공하기 전에는 기존 내용을 유지합니다. 수동 수정한 게시물, 처리 불가 게시물 및 진행 중인 작업이 있는 게시물은 실행할 수 없습니다.</Typography>
        <TextField label="재수집 사유" required multiline minRows={2} value={reason} disabled={saving} onChange={event => setReason(event.target.value)} slotProps={{ htmlInput: { maxLength: 500 } }} />
        {error && <Alert severity="error">{error}</Alert>}
      </Stack></DialogContent>
      <DialogActions><Button disabled={saving} onClick={() => setOpen(false)}>취소</Button><Button variant="contained" disabled={saving || !reason.trim()} onClick={submit}>{saving ? "예약 중…" : "캐시 초기화 후 재수집"}</Button></DialogActions>
    </Dialog>
  </>;
}
