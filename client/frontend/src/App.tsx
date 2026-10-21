import { Navigate, Route, Routes } from 'react-router-dom';
import { appConfig } from './config/appConfig';
import { AppShell } from './layout/AppShell';
import { AlertsPage } from './pages/AlertsPage';
import { ApisPage } from './pages/ApisPage';
import { InfraPage } from './pages/InfraPage';
import { OverviewPage } from './pages/OverviewPage';
import { RateLimitPage } from './pages/RateLimitPage';
import { StatsPage } from './pages/StatsPage';

export default function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path={appConfig.routes.hub} element={<OverviewPage />} />
        <Route path={appConfig.routes.apis} element={<ApisPage />} />
        <Route path={appConfig.routes.alerts} element={<AlertsPage />} />
        <Route path={appConfig.routes.rate} element={<RateLimitPage />} />
        <Route path={appConfig.routes.infra} element={<InfraPage />} />
        <Route path={appConfig.routes.stats} element={<StatsPage />} />
      </Route>
      <Route
        path={appConfig.routes.statsLegacy}
        element={<Navigate to={appConfig.routes.stats} replace />}
      />
      <Route path="*" element={<Navigate to={appConfig.routes.hub} replace />} />
    </Routes>
  );
}
