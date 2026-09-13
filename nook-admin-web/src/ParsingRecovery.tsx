import { Alert, Box, Button, Card, CardContent, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField, Typography } from "@mui/material";
import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "./api";

import { ParsingRecoveryTracking, recoveryStatusNames as statusNames, recoveryTypeNames as typeNames, type RecoveryJob as Job } from "./ParsingRecoveryTracking";

import { PostRecoveryDetails, PostRecoveryTracking, failedJobs, type RecoveryPost } from "./ParsingPostRecovery";
type Page = { posts: RecoveryPost[]; hasNext: boolean };

export function ParsingRecovery({ postId }: { postId?: string }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const watchedId = Number(searchParams.get("recoveryJobId"));
  const watchedPost = Number(searchParams.get("recoveryPostId"));
  const watch = (postId?: number) => setSearchParams(previous => {
    const next = new URLSearchParams(previous);
    next.delete("recoveryJobId");
    if (postId) next.set("recoveryPostId", String(postId)); else next.delete("recoveryPostId");
    return next;
  }, { replace: true });
  const [status, setStatus] = useState(postId ? "" : "FAILED");
  const [beforeId, setBeforeId] = useState<number>();
  const [page, setPage] = useState<Page>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const refreshList = useCallback(() => setRevision(value => value + 1), []);
  const [selected, setSelected] = useState<{ post: RecoveryPost; job?: Job }>();
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
    if (beforeId) query.set("beforePostId", String(beforeId));
    api<Page>(`/parsing-posts?${query}`).then(value => { if (active) setPage(value); })
      .catch((cause: unknown) => { if (active) setError(cause instanceof Error ? cause.message : "작업을 불러오지 못했습니다."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [postId, status, beforeId, revision]);
  const retry = async () => {
    if (!selected || saving) return;
    setSaving(true); setSaveError("");
    try {
      await api(selected.job ? `/parsing-jobs/${selected.job.id}/retry` : `/parsing-posts/${selected.post.postId}/retry`, { method: "POST", body: JSON.stringify({ reason }) });
      setNotice(`게시글 #${selected.post.postId}의 실패 작업 재시도를 예약했습니다.`);
      watch(selected.post.postId);
      setSelected(undefined); setReason(""); setRevision(value => value + 1);
    } catch (cause) { setSaveError(cause instanceof Error ? cause.message : "재시도 요청에 실패했습니다."); }
    finally { setSaving(false); }
  };
  return <Card variant="outlined"><CardContent><Stack spacing={2}>
    <Stack direction={{ xs: "column", lg: "row" }} spacing={1} sx={{ justifyContent: "space-between" }}>
      <Box><Typography variant="h6">게시물별 실패 복구</Typography><Typography variant="body2" color="text.secondary">같은 게시물의 본문·장소·미디어 작업을 함께 확인하고 실패한 단계들을 재시도합니다.</Typography></Box>
      <Stack direction="row" spacing={1}>
        <TextField select size="small" label="작업 상태" value={status} sx={{ minWidth: 130 }} onChange={event => { setStatus(event.target.value); setBeforeId(undefined); }}>
          <MenuItem value="">전체</MenuItem>{Object.entries(statusNames).map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}
        </TextField>
        <Button sx={{ whiteSpace: "nowrap", flexShrink: 0 }} onClick={() => setRevision(value => value + 1)} disabled={loading}>새로고침</Button>
      </Stack>
    </Stack>
    {notice && <Alert severity="success" onClose={() => setNotice("")}>{notice}</Alert>}
    {Number.isSafeInteger(watchedId) && watchedId > 0 && <ParsingRecoveryTracking key={watchedId} jobId={watchedId} onUpdate={refreshList} onClose={() => watch()} />}
    {Number.isSafeInteger(watchedPost) && watchedPost > 0 && <PostRecoveryTracking key={watchedPost} postId={watchedPost} onUpdate={refreshList} onClose={() => watch()} />}
    {error && <Alert severity="error">{error}</Alert>}
    {loading ? <Box sx={{ p: 2 }}><CircularProgress size={24} aria-label="게시물 작업 불러오는 중" /></Box> : page?.posts.length === 0 ? <Alert severity="info">이 조건에 해당하는 게시물이 없습니다.</Alert> : page?.posts.map(post => <Box key={post.postId} sx={{ borderTop: 1, borderColor: "divider", pt: 2 }}>
      <Stack spacing={1}>
        <PostRecoveryDetails post={post} onRetryJob={job => { setSelected({ post, job }); setReason(""); setSaveError(""); }} />
        <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
          <Button onClick={() => watch(post.postId)}>게시물 진행·결과 보기</Button>
          {failedJobs(post).length > 0 && <Button variant="outlined" onClick={() => { setSelected({ post }); setReason(""); setSaveError(""); }}>실패 작업 {failedJobs(post).length}건 함께 재시도</Button>}
        </Stack>
      </Stack>
    </Box>)}
    <Stack direction="row" spacing={1}>{beforeId && <Button onClick={() => setBeforeId(undefined)}>처음으로</Button>}{page?.hasNext && <Button disabled={loading} onClick={() => setBeforeId(page.posts.at(-1)?.postId)}>다음 게시물</Button>}</Stack>
  </Stack></CardContent>
    <Dialog open={!!selected} onClose={() => { if (!saving) setSelected(undefined); }} fullWidth maxWidth="sm">
      <DialogTitle>실패한 단계 재시도</DialogTitle><DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
        <Typography>게시글 #{selected?.post.postId}의 {selected?.job ? `${typeNames[selected.job.type]} 작업 1건` : `실패 작업 ${selected ? failedJobs(selected.post).length : 0}건`}을 재시도합니다. 완료된 작업은 유지하며 외부 서비스 호출 비용이 발생할 수 있습니다.</Typography>
        {selected && !selected.job && <Typography variant="body2">대상: {[...new Set(failedJobs(selected.post).map(job => typeNames[job.type] ?? job.type))].join(", ")}</Typography>}
        <TextField label="재처리 사유" multiline minRows={2} value={reason} onChange={event => setReason(event.target.value)} slotProps={{ htmlInput: { maxLength: 500 } }} disabled={saving} />
        {saveError && <Alert severity="error">{saveError}</Alert>}
      </Stack></DialogContent><DialogActions><Button disabled={saving} onClick={() => setSelected(undefined)}>취소</Button><Button variant="contained" disabled={saving || !reason.trim()} onClick={retry}>{saving ? "예약 중…" : "재시도 예약"}</Button></DialogActions>
    </Dialog>
  </Card>;
}
