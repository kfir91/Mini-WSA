import { appConfig } from './appConfig';

export type NavItem = {
  id: string;
  path: string;
  labelKey: string;
  end?: boolean;
};

/** Primary sidebar entries — shared by every page. */
export const navItems: NavItem[] = [
  { id: 'overview', path: appConfig.routes.hub, labelKey: 'nav.overview', end: true },
  { id: 'apis', path: appConfig.routes.apis, labelKey: 'nav.apis' },
  { id: 'alerts', path: appConfig.routes.alerts, labelKey: 'nav.alerts' },
  { id: 'rate', path: appConfig.routes.rate, labelKey: 'nav.rate' },
  { id: 'infra', path: appConfig.routes.infra, labelKey: 'nav.infra' },
  { id: 'stats', path: appConfig.routes.stats, labelKey: 'common.statsUi' },
];
