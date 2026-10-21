import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { fetchSummary, fetchTimeseries, type StatsQuery } from '../api/analyticsApi';
import { ApiError } from '../api/types';
import type { SummaryResponse, TimeseriesResponse } from '../api/types';
import { apiLinks, appConfig, type StatsInterval } from '../config/appConfig';

export type StatsFilters = {
  configId: string;
  interval: StatsInterval;
  fromLocal: string;
  toLocal: string;
  autoRefresh: boolean;
};

type StatsState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; summary: SummaryResponse; timeseries: TimeseriesResponse }
  | { status: 'error'; message: string };

function localToIso(value: string): string | undefined {
  if (!value) {
    return undefined;
  }
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) {
    return undefined;
  }
  return d.toISOString();
}

function toQuery(filters: StatsFilters): StatsQuery {
  return {
    configId: filters.configId,
    interval: filters.interval,
    from: localToIso(filters.fromLocal),
    to: localToIso(filters.toLocal),
  };
}

export function useStats(filters: StatsFilters) {
  const { t } = useTranslation();
  const [state, setState] = useState<StatsState>({ status: 'idle' });
  const filtersRef = useRef(filters);
  filtersRef.current = filters;

  const formatError = useCallback(
    (err: unknown): string => {
      if (err instanceof ApiError) {
        if (err.status === 429) {
          return t('messages.rateLimited', {
            limit: appConfig.defaults.rateLimitPerIpPerMin,
            statsPath: appConfig.paths.statsWildcard,
          });
        }
        return `${err.code}: ${err.message}`;
      }
      if (err instanceof TypeError) {
        return t('messages.networkError', { clientUrl: apiLinks.clientHub() });
      }
      return err instanceof Error ? err.message : t('common.requestFailed');
    },
    [t],
  );

  const load = useCallback(async () => {
    setState({ status: 'loading' });
    try {
      const q = toQuery(filtersRef.current);
      const [summary, timeseries] = await Promise.all([fetchSummary(q), fetchTimeseries(q)]);
      setState({ status: 'success', summary, timeseries });
    } catch (err) {
      setState({ status: 'error', message: formatError(err) });
    }
  }, [formatError]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!filters.autoRefresh) {
      return undefined;
    }
    const id = window.setInterval(() => {
      void load();
    }, appConfig.defaults.autoRefreshMs);
    return () => window.clearInterval(id);
  }, [filters.autoRefresh, load]);

  return { state, load };
}
