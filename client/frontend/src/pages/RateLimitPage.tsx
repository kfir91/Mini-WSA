import { useTranslation } from 'react-i18next';
import { Button } from '../components/ui/Button';
import { LogPanel } from '../components/ui/LogPanel';
import { SectionHead } from '../components/ui/SectionHead';
import { apiLinks, appConfig } from '../config/appConfig';
import { useRateLimitDemo } from '../hooks/useRateLimitDemo';

export function RateLimitPage() {
  const { t } = useTranslation();
  const { log, busy, hammer } = useRateLimitDemo();

  return (
    <section className="stack">
      <SectionHead title={t('hub.rateTitle')}>
        <strong>{appConfig.defaults.rateLimitPerIpPerMin}</strong> {t('hub.rateBodyBefore')}{' '}
        <code>{appConfig.paths.statsWildcard}</code> {t('hub.rateBodyAfter')}
      </SectionHead>

      <div className="toolbar">
        <Button
          disabled={busy}
          onClick={() => void hammer(apiLinks.summaryForConfig(), t('messages.demoLabelSummary'))}
        >
          {t('hub.hammerSummary')}
        </Button>
        <Button
          disabled={busy}
          onClick={() => void hammer(apiLinks.samples(1), t('messages.demoLabelSamples'))}
        >
          {t('hub.hammerSamples')}
        </Button>
      </div>

      <LogPanel text={log} />
    </section>
  );
}
