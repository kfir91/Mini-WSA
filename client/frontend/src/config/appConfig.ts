/**
 * Runtime UI config: hosts, ports, API paths, defaults.
 * Root .env WSA_* (and optional VITE_WSA_*) via Vite envPrefix — never hardcode in components.
 */

function envStr(...keys: string[]): string | undefined {
  for (const key of keys) {
    const value = (import.meta.env as Record<string, string | undefined>)[key];
    if (value != null && value !== '') {
      return value;
    }
  }
  return undefined;
}

function envPort(...keys: string[]): number | undefined {
  const raw = envStr(...keys);
  if (raw == null) {
    return undefined;
  }
  const n = Number(raw);
  return Number.isFinite(n) ? n : undefined;
}

const host = envStr('VITE_WSA_HOST', 'WSA_HOST') ?? 'localhost';

export const appConfig = {
  host,
  scheme: 'http' as const,

  ports: {
    client: envPort('VITE_WSA_CLIENT_PORT', 'WSA_CLIENT_PORT') ?? 8084,
    ingest: envPort('VITE_WSA_INGEST_PORT', 'WSA_INGEST_PORT') ?? 8081,
    analytics: envPort('VITE_WSA_ANALYTICS_PORT', 'WSA_ANALYTICS_PORT') ?? 8080,
    adminer: envPort('VITE_WSA_ADMINER_PORT', 'WSA_ADMINER_PORT') ?? 8082,
    kafkaUi: envPort('VITE_WSA_KAFKA_UI_PORT', 'WSA_KAFKA_UI_PORT') ?? 8083,
  },

  /** In-app React Router paths */
  routes: {
    hub: '/',
    apis: '/apis',
    alerts: '/alerts',
    rate: '/rate',
    infra: '/infra',
    stats: '/stats',
    statsLegacy: '/stats.html',
  },

  /** Same-origin (or absolute) HTTP paths */
  paths: {
    health: '/actuator/health',
    swaggerUi: '/swagger-ui/index.html',
    adminerGo: '/adminer-go.html',
    alertRules: '/alert-rules.json',
    statsSummary: '/v1/stats/summary',
    statsTimeseries: '/v1/stats/timeseries',
    statsWildcard: '/v1/stats/**',
    eventsSamples: '/v1/events/samples',
    alertsEvaluate: '/v1/alerts/evaluate',
    alertsDefine: '/v1/alerts/define',
    demoAlertsFire: '/demo/alerts/fire',
    hubSeedEvents: '/hub/seed/events',
  },

  defaults: {
    configId: '14227',
    interval: '5m' as const,
    intervals: ['1m', '5m', '1h'] as const,
    sampleLimit: 5,
    rateLimitPerIpPerMin: 100,
    autoRefreshMs: 30_000,
    probeTimeoutMs: 4_000,
    hammerMaxRequests: 250,
    hammerProgressEvery: 10,
    seedEventCount: 100,
    seedEventCountMax: 10_000,
  },

  healthTargets: ['client', 'ingest', 'analytics', 'adminer', 'kafkaUi'] as const,
} as const;

export type ServiceId = (typeof appConfig.healthTargets)[number];
export type StatsInterval = (typeof appConfig.defaults.intervals)[number];

export function originFor(port: number): string {
  return `${appConfig.scheme}://${appConfig.host}:${port}`;
}

export function serviceOrigin(id: ServiceId): string {
  return originFor(appConfig.ports[id]);
}

export function servicePortLabel(id: ServiceId): string {
  return `:${appConfig.ports[id]}`;
}

export function healthUrl(id: ServiceId): string {
  if (id === 'adminer' || id === 'kafkaUi') {
    return `${serviceOrigin(id)}/`;
  }
  return `${serviceOrigin(id)}${appConfig.paths.health}`;
}

export function swaggerUrl(id: 'ingest' | 'analytics'): string {
  return `${serviceOrigin(id)}${appConfig.paths.swaggerUi}`;
}

export function withQuery(
  path: string,
  params: Record<string, string | number | undefined>,
): string {
  const q = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === '') {
      continue;
    }
    q.set(key, String(value));
  }
  const s = q.toString();
  return s ? `${path}?${s}` : path;
}

export const apiLinks = {
  summary: () => appConfig.paths.statsSummary,
  summaryForConfig: (configId = appConfig.defaults.configId) =>
    withQuery(appConfig.paths.statsSummary, { configId }),
  timeseries: (
    interval: StatsInterval = appConfig.defaults.interval,
    configId = appConfig.defaults.configId,
  ) => withQuery(appConfig.paths.statsTimeseries, { interval, configId }),
  timeseriesIntervalOnly: (interval: StatsInterval = appConfig.defaults.interval) =>
    withQuery(appConfig.paths.statsTimeseries, { interval }),
  samples: (limit: number = appConfig.defaults.sampleLimit) =>
    withQuery(appConfig.paths.eventsSamples, { limit }),
  alertsEvaluate: () => appConfig.paths.alertsEvaluate,
  alertsDefine: () => appConfig.paths.alertsDefine,
  demoAlertsFire: () => appConfig.paths.demoAlertsFire,
  hubSeedEvents: () => appConfig.paths.hubSeedEvents,
  alertRules: () => appConfig.paths.alertRules,
  adminerConnect: () => `${serviceOrigin('analytics')}${appConfig.paths.adminerGo}`,
  clientHub: () => `${serviceOrigin('client')}/`,
  kafkaUi: () => `${serviceOrigin('kafkaUi')}/`,
};
