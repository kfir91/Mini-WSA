import { Outlet } from 'react-router-dom';
import { AppHeader } from './AppHeader';
import { SideNav } from './SideNav';

export function AppShell() {
  return (
    <div className="layout">
      <AppHeader />
      <SideNav />
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
