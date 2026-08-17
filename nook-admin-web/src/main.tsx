import AssignmentTurnedInIcon from "@mui/icons-material/AssignmentTurnedIn";
import DashboardIcon from "@mui/icons-material/Dashboard";
import HistoryIcon from "@mui/icons-material/History";
import MapIcon from "@mui/icons-material/Map";
import PlaceIcon from "@mui/icons-material/Place";
import ReplayIcon from "@mui/icons-material/Replay";
import { Box, Button, Card, CardContent, Chip, Grid, Stack, Typography } from "@mui/material";
import { createTheme } from "@mui/material/styles";
import React from "react";
import {
  Admin,
  CustomRoutes,
  Layout,
  Menu,
  type DataProvider,
  type LayoutProps,
} from "react-admin";
import { createRoot } from "react-dom/client";
import { Route } from "react-router-dom";
import "./styles.css";

const theme = createTheme({
  palette: {
    mode: "light",
    primary: {
      main: "#1f2937",
    },
    secondary: {
      main: "#b7791f",
    },
    background: {
      default: "#f5f7fa",
    },
  },
  shape: {
    borderRadius: 8,
  },
  typography: {
    fontFamily:
      'Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
  },
});

const dataProvider: DataProvider = {
  getList: async () => ({ data: [], total: 0 }),
  getOne: async (_resource, params) => ({ data: { id: params.id } as any }),
  getMany: async () => ({ data: [] }),
  getManyReference: async () => ({ data: [], total: 0 }),
  create: async (_resource, params) => ({ data: { id: Date.now(), ...params.data } as any }),
  update: async (_resource, params) => ({ data: { id: params.id, ...params.data } as any }),
  updateMany: async (_resource, params) => ({ data: params.ids }),
  delete: async (_resource, params) => ({ data: (params.previousData ?? { id: params.id }) as any }),
  deleteMany: async (_resource, params) => ({ data: params.ids }),
};

function App() {
  return (
    <Admin
      basename="/"
      dashboard={Dashboard}
      dataProvider={dataProvider}
      disableTelemetry
      layout={AdminLayout}
      theme={theme}
      title="Nook Admin"
    >
      <CustomRoutes>
        <Route
          path="/places"
          element={
            <PlaceholderPage
              icon={<PlaceIcon />}
              title="장소"
              description="장소 검색, 상세 확인, 수동 보정 화면이 들어갈 영역입니다."
            />
          }
        />
        <Route
          path="/parsing-jobs"
          element={
            <PlaceholderPage
              icon={<ReplayIcon />}
              title="파싱 작업"
              description="실패한 파싱 작업 확인과 재시도 액션을 연결할 영역입니다."
            />
          }
        />
        <Route
          path="/audit-logs"
          element={
            <PlaceholderPage
              icon={<HistoryIcon />}
              title="감사 로그"
              description="운영자가 수행한 변경 내역과 사유를 추적할 영역입니다."
            />
          }
        />
      </CustomRoutes>
    </Admin>
  );
}

function AdminLayout(props: LayoutProps) {
  return <Layout {...props} menu={AdminMenu} />;
}

function AdminMenu() {
  return (
    <Menu>
      <Menu.DashboardItem leftIcon={<DashboardIcon />} />
      <Menu.Item to="/places" primaryText="장소" leftIcon={<PlaceIcon />} />
      <Menu.Item to="/parsing-jobs" primaryText="파싱 작업" leftIcon={<ReplayIcon />} />
      <Menu.Item to="/audit-logs" primaryText="감사 로그" leftIcon={<HistoryIcon />} />
    </Menu>
  );
}

function Dashboard() {
  return (
    <Box sx={{ display: "grid", gap: 3 }}>
      <Stack direction="row" sx={{ justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="overline" color="text.secondary" sx={{ fontWeight: 700 }}>
            Cloudflare Access
          </Typography>
          <Typography variant="h4" sx={{ fontWeight: 800 }}>
            Nook 운영 콘솔
          </Typography>
        </Box>
        <Chip color="success" label="Google 로그인 보호" />
      </Stack>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 4 }}>
          <SummaryCard icon={<MapIcon />} label="장소 보정" value="준비 중" />
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <SummaryCard icon={<ReplayIcon />} label="파싱 작업" value="준비 중" />
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <SummaryCard icon={<AssignmentTurnedInIcon />} label="감사 로그" value="예정" />
        </Grid>
      </Grid>

      <Card variant="outlined">
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>
              인프라 상태
            </Typography>
            <Typography color="text.secondary">
              이 앱은 ops VM에서 nginx 정적 컨테이너로 서빙하고, 외부 접근은 Cloudflare
              Tunnel과 Access로 통제합니다. 실제 기능은 `/api/admin/v1/**` API가 준비된 뒤
              React Admin resource와 custom action으로 연결합니다.
            </Typography>
            <Stack direction="row" sx={{ gap: 1, flexWrap: "wrap" }}>
              <Chip label="React Admin" />
              <Chip label="MUI" />
              <Chip label="Vite" />
              <Chip label="Nginx static" />
            </Stack>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
}

function SummaryCard({
  icon,
  label,
  value,
}: {
  icon: React.ReactElement;
  label: string;
  value: string;
}) {
  return (
    <Card variant="outlined" sx={{ height: "100%" }}>
      <CardContent>
        <Stack direction="row" sx={{ alignItems: "center", gap: 2 }}>
          <Box className="summary-icon">{icon}</Box>
          <Box>
            <Typography color="text.secondary" variant="body2">
              {label}
            </Typography>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>
              {value}
            </Typography>
          </Box>
        </Stack>
      </CardContent>
    </Card>
  );
}

function PlaceholderPage({
  icon,
  title,
  description,
}: {
  icon: React.ReactElement;
  title: string;
  description: string;
}) {
  return (
    <Card variant="outlined">
      <CardContent>
        <Stack spacing={2} sx={{ alignItems: "flex-start" }}>
          <Box className="summary-icon">{icon}</Box>
          <Box>
            <Typography variant="h5" sx={{ fontWeight: 800 }}>
              {title}
            </Typography>
            <Typography color="text.secondary" sx={{ mt: 1 }}>
              {description}
            </Typography>
          </Box>
          <Button disabled variant="contained">
            API 준비 후 연결
          </Button>
        </Stack>
      </CardContent>
    </Card>
  );
}

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
