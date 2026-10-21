import { appConfig } from '../config/appConfig';
import { requestJson } from './http';
import type {
  AlertEvalRow,
  DemoAlertRule,
  SummaryResponse,
  TimeseriesResponse,
} from './types';

export type StatsQuery = {
  configId?: string;
  interval?: (typeof appConfig.defaults.intervals)[number];
  from?: string;
  to?: string;
};

function querySuffix(params: StatsQuery, includeInterval: boolean): string {
  const q = new URLSearchParams();
  if (includeInterval && params.interval) {
    q.set('interval', params.interval);
  }
  if (params.configId?.trim()) {
    q.set('configId', params.configId.trim());
  }
  if (params.from) {
    q.set('from', params.from);
  }
  if (params.to) {
    q.set('to', params.to);
  }
  const s = q.toString();
  return s ? `?${s}` : '';
}

export function fetchSummary(params: StatsQuery): Promise<SummaryResponse> {
  return requestJson<SummaryResponse>(
    `${appConfig.paths.statsSummary}${querySuffix(params, false)}`,
  );
}

export function fetchTimeseries(params: StatsQuery): Promise<TimeseriesResponse> {
  return requestJson<TimeseriesResponse>(
    `${appConfig.paths.statsTimeseries}${querySuffix(params, true)}`,
  );
}

export function evaluateAlerts(): Promise<AlertEvalRow[]> {
  return requestJson<AlertEvalRow[]>(appConfig.paths.alertsEvaluate);
}

export function defineAlert(rule: Record<string, unknown>): Promise<{ id: string }> {
  return requestJson<{ id: string }>(appConfig.paths.alertsDefine, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(rule),
  });
}

export function fireDemoAlerts(): Promise<Record<string, unknown>> {
  return requestJson<Record<string, unknown>>(appConfig.paths.demoAlertsFire, {
    method: 'POST',
  });
}

export async function loadDemoAlertRules(): Promise<DemoAlertRule[]> {
  return requestJson<DemoAlertRule[]>(appConfig.paths.alertRules);
}

export async function getText(url: string): Promise<{ status: number; body: string }> {
  const res = await fetch(url, { cache: 'no-store' });
  return { status: res.status, body: await res.text() };
}

/**
 * Reachability probe for the hub health strip.
 * Uses {@code no-cors} for cross-origin localhost services (opaque response means
 * the TCP/HTTP stack answered — not a guarantee of HTTP 200).
 */
export async function probeUrl(
  url: string,
  timeoutMs = appConfig.defaults.probeTimeoutMs,
): Promise<boolean> {
  const ctrl = new AbortController();
  const timer = window.setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    await fetch(url, { signal: ctrl.signal, mode: 'no-cors', cache: 'no-store' });
    return true;
  } catch {
    return false;
  } finally {
    window.clearTimeout(timer);
  }
}
