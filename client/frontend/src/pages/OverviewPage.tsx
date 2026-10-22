import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { SeedEventsCard } from '../components/SeedEventsCard';
import { ServiceCard } from '../components/ServiceCard';
import { Card } from '../components/ui/Card';
import { ChipLink } from '../components/ui/ChipLink';
import { SectionHead } from '../components/ui/SectionHead';
import { apiLinks, appConfig, swaggerUrl } from '../config/appConfig';
import { useServiceHealth } from '../hooks/useServiceHealth';

export function OverviewPage() {
  const { t } = useTranslation();
  const probes = useServiceHealth();

  return (
    <section className="stack">
      <SectionHead title={t('hub.overviewTitle')}>
        {t('hub.overviewBodyBefore')} <code>{t('common.client')}</code>
        {t('hub.overviewBodyAfter')}
      </SectionHead>

      <div className="grid">
        <ServiceCard
          id="client"
          title={t('services.client')}
          description={t('hub.serviceClientDesc')}
          probe={probes.client}
          waitLabel={t('common.ellipsis')}
          actions={
            <>
              <a href={apiLinks.clientHub()}>{t('common.hub')}</a>
              <Link to={appConfig.routes.stats}>{t('common.statsUi')}</Link>
            </>
          }
        />
        <ServiceCard
          id="ingest"
          title={t('services.ingest')}
          description={t('hub.serviceIngestDesc')}
          probe={probes.ingest}
          waitLabel={t('common.ellipsis')}
          actions={<a href={swaggerUrl('ingest')}>{t('common.swagger')}</a>}
        />
        <ServiceCard
          id="analytics"
          title={t('services.analytics')}
          description={t('hub.serviceAnalyticsDesc')}
          probe={probes.analytics}
          waitLabel={t('common.ellipsis')}
          actions={
            <>
              <a href={swaggerUrl('analytics')}>{t('common.swagger')}</a>
              <a href={apiLinks.adminerConnect()}>{t('hub.adminerConnect')}</a>
            </>
          }
        />
      </div>

      <Card title={t('hub.quickChecks')} spaced>
        <div className="chip-row">
          <ChipLink to={appConfig.routes.stats}>{t('common.statsUi')}</ChipLink>
          <ChipLink href={apiLinks.summary()}>{t('hub.chipSummary')}</ChipLink>
          <ChipLink href={apiLinks.timeseriesIntervalOnly()}>
            {t('hub.chipTimeseries', { interval: appConfig.defaults.interval })}
          </ChipLink>
          <ChipLink href={apiLinks.alertsEvaluate()}>{t('hub.chipAlertsEvaluate')}</ChipLink>
        </div>
      </Card>

      <SeedEventsCard />
    </section>
  );
}
