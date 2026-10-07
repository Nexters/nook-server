import { Box, Card, CardContent, Stack, Typography } from "@mui/material";

export type SavedUser = { id: number; nickname: string };

export function SavedUsersInline({ users }: { users?: SavedUser[] }) {
  if (!users) return <Typography variant="body2" color="text.secondary">저장 회원 정보를 확인 중입니다.</Typography>;
  if (users.length === 0) return <Typography variant="body2" color="text.secondary">없음</Typography>;
  return <Stack spacing={.5}>{users.map(user => <Typography key={user.id} variant="body2" sx={{ overflowWrap: "anywhere" }}>{user.nickname} <Box component="span" className="mono-text" sx={{ color: "text.secondary" }}>({user.id})</Box></Typography>)}</Stack>;
}

export function SavedUsersSection({ users }: { users?: SavedUser[] }) {
  return <Box><Typography variant="h6" sx={{ mb: 1.5 }}>저장한 회원{users && ` · ${users.length}명`}</Typography>{!users ? <SavedUsersInline /> : users.length === 0 ? <Typography variant="body2" color="text.secondary">현재 저장한 회원이 없습니다.</Typography> : <Stack spacing={1}>{users.map(user => <Card variant="outlined" key={user.id}><CardContent sx={{ display: "flex", gap: 1.5, alignItems: "center", py: 1.25, "&:last-child": { pb: 1.25 } }}><Typography variant="body2" color="text.secondary" sx={{ minWidth: 88 }}>회원 ID <span className="mono-text">{user.id}</span></Typography><Typography variant="body2" sx={{ overflowWrap: "anywhere", minWidth: 0 }}>{user.nickname}</Typography></CardContent></Card>)}</Stack>}</Box>;
}
