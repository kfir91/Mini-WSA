import { useTranslation } from 'react-i18next';
import { appConfig } from '../config/appConfig';
import { useIngestStatus } from '../hooks/useIngestStatus';
import { Button } from './ui/Button';
import { Card } from './ui/Card';
import { LogPanel } from './ui/LogPanel';

export function IngestStatusCard() {
  const { t } = useTranslation();
  const { eventId, setEventId, busy, log, status, poll } = useIngestStatus();

  return (
    <Card title={t('hub.statusTitle')} spaced>
      <p className="meta">{t('hub.statusBody')}</p>
      <p className="meta">
        <code>GET {appConfig.paths.hubEventStatusBase}/{'{eventId}'}/status</code>
      </p>
      <div className="form-row form-row--tight-top">
        <label className="field field--wide">
          <span>{t('hub.statusEventId')}</span>
          <input
            type="text"
            value={eventId}
            disabled={busy}
            placeholder="evt-…"
            onChange={(e) => setEventId(e.target.value)}
          />
        </label>
        <Button variant="primary" disabled={busy} onClick={() => void poll()}>
          {busy ? t('hub.statusPolling') : t('hub.statusPoll')}
        </Button>
      </div>
      {status ? (
        <p className="meta">
          <span className={`status-chip status-chip--${status.status}`}>{status.status}</span>
          {' · '}
          {t('hub.statusAttempts', { count: status.attemptCount })}
        </p>
      ) : null}
      {log ? <LogPanel text={log} /> : null}
    </Card>
  );
}
