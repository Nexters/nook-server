import AssignmentTurnedInIcon from "@mui/icons-material/AssignmentTurnedIn";
import DashboardIcon from "@mui/icons-material/Dashboard";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import PlaceIcon from "@mui/icons-material/Place";
import { Alert, Autocomplete, Box, Button, Card, CardContent, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField, Typography } from "@mui/material";
import { createTheme } from "@mui/material/styles";
import React, { useEffect, useState } from "react";
import { Admin, Datagrid, DateField, FunctionField, Layout, List, Menu, Resource, Show, SimpleShowLayout, TextField as RaTextField, TextInput, TopToolbar, type DataProvider, type LayoutProps, useNotify, useRecordContext, useRefresh } from "react-admin";
import { createRoot } from "react-dom/client";
import "./styles.css";

const apiBase = (import.meta.env.VITE_ADMIN_API_BASE_URL ?? "").replace(/\/$/, "");
type Envelope<T> = { success?: T; error?: { reason?: string } };
type Page<T> = { items: T[]; total: number };
type Place = { id: number; name: string; address: string; provider: string; externalPlaceId: string };
type PostDetail = { id: number; canonicalUrl: string; authorIdentifier?: string; title?: string; body?: string; contentParsingStatus: string; contentParsingFailureReason?: string; placeParsingStatus?: string; placeParsingFailureReason?: string; savedUserCount: number; mappingReviewed: boolean; places: Array<Place & { sequence: number }> };
type ManagedPlace = Place & { linkedPostCount: number; affectedUserCount: number; posts: Array<{ id: number; title?: string; authorIdentifier?: string; canonicalUrl: string; createdAt: string }> };

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBase}/api/admin/v1${path}`, { credentials: "include", ...init, headers: { "Content-Type": "application/json", ...init?.headers } });
  const envelope = (await response.json().catch(() => ({}))) as Envelope<T>;
  if (!response.ok || envelope.success === undefined) throw new Error(envelope.error?.reason ?? `요청에 실패했습니다. (${response.status})`);
  return envelope.success;
}

const dataProvider: DataProvider = {
  getList: async (resource, params) => {
    const limit = params.pagination?.perPage ?? 20;
    const offset = ((params.pagination?.page ?? 1) - 1) * limit;
    const search = new URLSearchParams({ offset: String(offset), limit: String(limit) });
    Object.entries(params.filter ?? {}).forEach(([key, value]) => value && search.set(key, String(value)));
    const path = resource === "places" ? `/places/manage?${search}` : `/${resource}?${search}`;
    const result = await api<Page<Record<string, unknown>>>(path);
    return { data: result.items as never[], total: result.total };
  },
  getOne: async (resource, params) => ({ data: await api(`/${resource}/${params.id}`) }),
  getMany: async () => ({ data: [] }), getManyReference: async () => ({ data: [], total: 0 }),
  create: async () => Promise.reject(new Error("지원하지 않는 동작입니다.")), update: async () => Promise.reject(new Error("지원하지 않는 동작입니다.")), updateMany: async () => Promise.reject(new Error("지원하지 않는 동작입니다.")), delete: async () => Promise.reject(new Error("지원하지 않는 동작입니다.")), deleteMany: async () => Promise.reject(new Error("지원하지 않는 동작입니다.")),
};

const theme = createTheme({ palette: { primary: { main: "#1f2937" }, secondary: { main: "#b7791f" }, background: { default: "#f5f7fa" } }, shape: { borderRadius: 8 }, typography: { fontFamily: 'Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif' } });

function App() { return <Admin dashboard={Dashboard} dataProvider={dataProvider} disableTelemetry layout={AdminLayout} theme={theme} title="Nook Admin"><Resource name="posts" options={{ label: "게시글 관리" }} icon={AssignmentTurnedInIcon} list={PostList} show={PostShow} /><Resource name="places" options={{ label: "장소 관리" }} icon={PlaceIcon} list={PlaceList} show={PlaceShow} /><Resource name="audit-logs" options={{ label: "감사 로그" }} icon={AssignmentTurnedInIcon} list={AuditLogList} /></Admin>; }
function AdminLayout(props: LayoutProps) { return <Layout {...props} menu={AdminMenu} />; }
function AdminMenu() { return <Menu><Menu.DashboardItem leftIcon={<DashboardIcon />} /><Menu.ResourceItem name="posts" /><Menu.ResourceItem name="places" /><Menu.ResourceItem name="audit-logs" /></Menu>; }

function Dashboard() {
  const [email, setEmail] = useState<string>();
  useEffect(() => { api<{ email: string }>("/me").then((actor) => setEmail(actor.email)).catch(() => undefined); }, []);
  return <Stack spacing={3}><Box><Typography variant="overline" color="text.secondary" sx={{ fontWeight: 700 }}>Cloudflare Access</Typography><Typography variant="h4" sx={{ fontWeight: 800 }}>Nook 운영 콘솔</Typography></Box><Alert severity="success">{email ? `${email} 계정으로 인증되었습니다.` : "인증 정보를 확인하고 있습니다."}</Alert><Card variant="outlined"><CardContent><Typography variant="h6" sx={{ fontWeight: 800 }}>운영 원칙</Typography><Typography color="text.secondary" sx={{ mt: 1 }}>공용 매핑 교정은 이후 저장 건에 적용됩니다. 기존 사용자의 저장 장소는 변경하지 않으며 모든 교정에는 사유가 필요합니다.</Typography></CardContent></Card></Stack>;
}

function PostList() { return <List filters={[<TextInput key="query" source="query" label="제목·작성자·URL 검색" alwaysOn />]}><Datagrid rowClick="show" bulkActionButtons={false}><RaTextField source="id" label="ID" /><RaTextField source="title" label="제목" /><RaTextField source="authorIdentifier" label="작성자" /><RaTextField source="contentParsingStatus" label="게시글 파싱" /><RaTextField source="placeParsingStatus" label="장소 파싱" /><RaTextField source="placeCount" label="장소 수" /><RaTextField source="savedUserCount" label="저장 사용자" /><FunctionField label="검수" render={(record: { mappingReviewed: boolean }) => <Chip size="small" color={record.mappingReviewed ? "success" : "default"} label={record.mappingReviewed ? "완료" : "미검수"} />} /><DateField source="createdAt" label="생성일" showTime /></Datagrid></List>; }
function PostShow() { return <Show actions={<PostActions />}><SimpleShowLayout><PostPanel /></SimpleShowLayout></Show>; }
function PostActions() { const record = useRecordContext<PostDetail>(); return <TopToolbar>{record && <Button href={record.canonicalUrl} target="_blank" endIcon={<OpenInNewIcon />}>원문 열기</Button>}</TopToolbar>; }

function PostPanel() {
  const record = useRecordContext<PostDetail>(); const [open, setOpen] = useState(false); if (!record) return null;
  const failure = record.contentParsingFailureReason ?? record.placeParsingFailureReason;
  return <Stack spacing={3}><Stack direction="row" spacing={1}><Chip label={`게시글 ${record.contentParsingStatus}`} /><Chip label={`장소 ${record.placeParsingStatus ?? "미시작"}`} /><Chip color={record.mappingReviewed ? "success" : "default"} label={record.mappingReviewed ? "관리자 검수 완료" : "미검수"} /></Stack>{failure && <Alert severity="error">{failure}</Alert>}<Box><Typography variant="h6" sx={{ fontWeight: 800 }}>{record.title ?? "제목 없음"}</Typography><Typography color="text.secondary">{record.authorIdentifier ?? "작성자 미상"} · 저장 사용자 {record.savedUserCount}명</Typography><Typography sx={{ whiteSpace: "pre-wrap", mt: 2 }}>{record.body}</Typography></Box><Box><Stack direction="row" sx={{ justifyContent: "space-between" }}><Typography variant="h6" sx={{ fontWeight: 800 }}>공용 장소 매핑</Typography><Button variant="contained" onClick={() => setOpen(true)}>매핑 교정</Button></Stack><Stack spacing={1} sx={{ mt: 2 }}>{record.places.map((place) => <Card variant="outlined" key={place.id}><CardContent><Typography sx={{ fontWeight: 700 }}>{place.sequence + 1}. {place.name}</Typography><Typography color="text.secondary">{place.address}</Typography><Typography variant="caption">{place.provider} · {place.externalPlaceId}</Typography></CardContent></Card>)}</Stack></Box><MappingDialog open={open} post={record} onClose={() => setOpen(false)} /></Stack>;
}

function MappingDialog({ open, post, onClose }: { open: boolean; post: PostDetail; onClose: () => void }) {
  const [selected, setSelected] = useState<Place[]>(post.places); const [options, setOptions] = useState<Place[]>(post.places); const [query, setQuery] = useState(""); const [reason, setReason] = useState(""); const [saving, setSaving] = useState(false); const notify = useNotify(); const refresh = useRefresh();
  useEffect(() => { if (query.trim().length < 2) return; const timer = window.setTimeout(() => api<Place[]>(`/places?query=${encodeURIComponent(query)}`).then((items) => setOptions([...selected, ...items].filter((item, index, all) => all.findIndex((candidate) => candidate.id === item.id) === index))), 250); return () => window.clearTimeout(timer); }, [query, selected]);
  const save = async () => { setSaving(true); try { await api(`/posts/${post.id}/places`, { method: "PUT", body: JSON.stringify({ placeIds: selected.map((place) => place.id), reason }) }); notify("매핑을 교정하고 감사 로그를 남겼습니다.", { type: "success" }); refresh(); onClose(); } catch (error) { notify(error instanceof Error ? error.message : "교정에 실패했습니다.", { type: "error" }); } finally { setSaving(false); } };
  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="md"><DialogTitle>공용 장소 매핑 교정</DialogTitle><DialogContent><Stack spacing={2} sx={{ mt: 1 }}><Alert severity="info">기존 사용자 {post.savedUserCount}명의 저장 장소는 유지되고 이후 저장 건에만 적용됩니다. 장소를 모두 제거하는 교정도 가능합니다.</Alert><Autocomplete multiple options={options} value={selected} onChange={(_, value) => setSelected(value)} onInputChange={(_, value) => setQuery(value)} getOptionLabel={(place) => `${place.name} · ${place.address}`} isOptionEqualToValue={(a, b) => a.id === b.id} renderInput={(params) => <TextField {...params} label="장소 검색" helperText="두 글자 이상 입력하세요." />} /><TextField label="수정 사유" required multiline minRows={3} value={reason} onChange={(event) => setReason(event.target.value)} /></Stack></DialogContent><DialogActions><Button onClick={onClose}>취소</Button><Button variant="contained" disabled={saving || !reason.trim()} onClick={save}>{saving ? <CircularProgress size={20} /> : "교정 저장"}</Button></DialogActions></Dialog>;
}

function PlaceList() { return <List filters={[<TextInput key="query" source="query" label="장소명·주소·외부 ID 검색" alwaysOn />]}><Datagrid rowClick="show" bulkActionButtons={false}><RaTextField source="id" label="ID" /><RaTextField source="name" label="장소명" /><RaTextField source="address" label="주소" /><RaTextField source="provider" label="Provider" /><RaTextField source="externalPlaceId" label="외부 ID" /><RaTextField source="linkedPostCount" label="연결 게시글" /><RaTextField source="affectedUserCount" label="영향 사용자" /></Datagrid></List>; }
function PlaceShow() { return <Show><SimpleShowLayout><PlacePanel /></SimpleShowLayout></Show>; }
function PlacePanel() {
  const record = useRecordContext<ManagedPlace>(); const [open, setOpen] = useState(false); if (!record) return null;
  return <Stack spacing={3}><Alert severity="warning">이 장소는 게시글 {record.linkedPostCount}개와 저장 사용자 {record.affectedUserCount}명에게 노출됩니다. 장소명·주소 수정은 기존 사용자 화면에도 즉시 반영됩니다.</Alert><Box><Typography variant="h5" sx={{ fontWeight: 800 }}>{record.name}</Typography><Typography color="text.secondary" sx={{ mt: 1 }}>{record.address}</Typography><Typography variant="caption">{record.provider} · {record.externalPlaceId}</Typography></Box><Box><Button variant="contained" onClick={() => setOpen(true)}>장소 정보 수정</Button></Box><Box><Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>연결 게시글</Typography><Stack spacing={1}>{record.posts.map((post) => <Card variant="outlined" key={post.id}><CardContent><Typography sx={{ fontWeight: 700 }}>{post.title ?? "제목 없음"}</Typography><Typography color="text.secondary">{post.authorIdentifier ?? "작성자 미상"}</Typography><Button size="small" href={`#/posts/${post.id}/show`}>게시글 관리에서 보기</Button><Button size="small" href={post.canonicalUrl} target="_blank" endIcon={<OpenInNewIcon />}>원문</Button></CardContent></Card>)}</Stack></Box><PlaceEditDialog open={open} place={record} onClose={() => setOpen(false)} /></Stack>;
}
function PlaceEditDialog({ open, place, onClose }: { open: boolean; place: ManagedPlace; onClose: () => void }) {
  const [name, setName] = useState(place.name); const [address, setAddress] = useState(place.address); const [reason, setReason] = useState(""); const [saving, setSaving] = useState(false); const notify = useNotify(); const refresh = useRefresh();
  const save = async () => { setSaving(true); try { await api(`/places/${place.id}`, { method: "PUT", body: JSON.stringify({ name, address, reason }) }); notify("장소 정보를 수정하고 감사 로그를 남겼습니다.", { type: "success" }); refresh(); onClose(); } catch (error) { notify(error instanceof Error ? error.message : "수정에 실패했습니다.", { type: "error" }); } finally { setSaving(false); } };
  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm"><DialogTitle>공용 장소 정보 수정</DialogTitle><DialogContent><Stack spacing={2} sx={{ mt: 1 }}><Alert severity="warning">게시글 {place.linkedPostCount}개와 저장 사용자 {place.affectedUserCount}명의 화면에 변경된 정보가 표시됩니다.</Alert><TextField label="장소명" required value={name} onChange={(event) => setName(event.target.value)} /><TextField label="주소" required value={address} onChange={(event) => setAddress(event.target.value)} /><TextField label="수정 사유" required multiline minRows={3} value={reason} onChange={(event) => setReason(event.target.value)} /></Stack></DialogContent><DialogActions><Button onClick={onClose}>취소</Button><Button variant="contained" disabled={saving || !name.trim() || !address.trim() || !reason.trim()} onClick={save}>{saving ? <CircularProgress size={20} /> : "수정 저장"}</Button></DialogActions></Dialog>;
}

function AuditLogList() { return <List filters={[<TextInput key="targetType" source="targetType" label="대상 유형" />, <TextInput key="targetId" source="targetId" label="대상 ID" />]}><Datagrid bulkActionButtons={false} expand={<AuditDetails />}><DateField source="createdAt" label="시각" showTime /><RaTextField source="actorEmail" label="운영자" /><RaTextField source="action" label="동작" /><RaTextField source="targetType" label="대상" /><RaTextField source="targetId" label="대상 ID" /><RaTextField source="reason" label="사유" /><RaTextField source="requestId" label="Request ID" /></Datagrid></List>; }
function AuditDetails() { const record = useRecordContext<{ beforeValue?: string; afterValue?: string }>(); if (!record) return null; return <Stack direction={{ xs: "column", md: "row" }} spacing={2} sx={{ p: 2 }}><Box sx={{ flex: 1 }}><Typography variant="subtitle2">변경 전</Typography><Box component="pre" sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{formatJson(record.beforeValue)}</Box></Box><Box sx={{ flex: 1 }}><Typography variant="subtitle2">변경 후</Typography><Box component="pre" sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{formatJson(record.afterValue)}</Box></Box></Stack>; }
function formatJson(value?: string) { if (!value) return "-"; try { return JSON.stringify(JSON.parse(value), null, 2); } catch { return value; } }

createRoot(document.getElementById("root")!).render(<React.StrictMode><App /></React.StrictMode>);
