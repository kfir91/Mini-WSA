import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { StatsFilters } from '../components/StatsFilters';
import { SummaryTables } from '../components/SummaryTables';
import { TimeseriesChart } from '../components/TimeseriesChart';
import { Card } from '../components/ui/Card';
import { SectionHead } from '../components/ui/SectionHead';
import { StatusBanner } from '../components/ui/StatusBanner';
import { appConfig } from '../config/appConfig';
import { useStats, type StatsFilters as Filters } from '../hooks/useStats';

const initialFilters: Filters = {
  configId: appConfig.defaults.configId,
  interval: appConfig.defaults.interval,
  fromLocal: '',
  toLocal: '',
  autoRefresh: false,
};

export function StatsPage() {
  const { t } = useTranslation();
  const [filters, setFilters] = useState<Filters>(initialFilters);
  const { state, load } = useStats(filters);

  return (
    <section className="stack" id="stats-page">
      <SectionHead title={t('stats.title')}>
        {t('stats.introBefore')} <code>{appConfig.paths.statsTimeseries}</code>{' '}
        {t('stats.introMiddle')} <code>{appConfig.paths.statsSummary}</code>{' '}
        {t('stats.introAfter')} <strong>{t('common.client')}</strong>{' '}
        {t('stats.introTail', { limit: appConfig.defaults.rateLimitPerIpPerMin })}
      </SectionHead>

      <StatsFilters
        filters={filters}
        loading={state.status === 'loading'}
        onChange={setFilters}
        onLoad={() => void load()}
      />

      {state.status === 'loading' && <StatusBanner tone="info">{t('common.loading')}</StatusBanner>}
      {state.status === 'error' && <StatusBanner tone="error">{state.message}</StatusBanner>}

      {state.status === 'success' && (
        <>
          <SummaryTables summary={state.summary} />
          <Card title={`${t('stats.timeseries')} (${state.timeseries.interval})`} ariaLabel={t('stats.chartAria')}>
            <TimeseriesChart timeseries={state.timeseries} />
          </Card>
        </>
      )}
    </section>
  );
}
