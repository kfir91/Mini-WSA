import { useTranslation } from 'react-i18next';
import { appConfig } from '../config/appConfig';
import { useSeedEvents } from '../hooks/useSeedEvents';
import { Button } from './ui/Button';
import { Card } from './ui/Card';
import { LogPanel } from './ui/LogPanel';

export function SeedEventsCard() {
  const { t } = useTranslation();
  const { count, setCount, configId, setConfigId, busy, log, run } = useSeedEvents();

  return (
    <Card title={t('hub.seedTitle')} spaced>
      <p className="meta">{t('hub.seedBody')}</p>
      <p className="meta">
        <code>POST {appConfig.paths.hubSeedEvents}</code>
      </p>
      <div className="form-row form-row--tight-top">
        <label className="field">
          <span>{t('hub.seedCount')}</span>
          <input
            type="number"
            min={1}
            max={appConfig.defaults.seedEventCountMax}
            value={count}
            disabled={busy}
            onChange={(e) => setCount(Number(e.target.value))}
          />
        </label>
        <label className="field">
          <span>{t('hub.seedConfigId')}</span>
          <input
            type="text"
            inputMode="numeric"
            value={configId}
            disabled={busy}
            onChange={(e) => setConfigId(e.target.value)}
          />
        </label>
        <Button variant="primary" disabled={busy} onClick={() => void run()}>
          {busy ? t('hub.seedRunning') : t('hub.seedRun')}
        </Button>
      </div>
      {log ? <LogPanel text={log} /> : null}
    </Card>
  );
}
