import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { fetchIngestStatus } from '../api/hubApi';
import type { IngestEventStatus } from '../api/types';
import { apiLinks } from '../config/appConfig';

export function useIngestStatus() {
  const { t } = useTranslation();
  const [eventId, setEventId] = useState('');
  const [busy, setBusy] = useState(false);
  const [log, setLog] = useState('');
  const [status, setStatus] = useState<IngestEventStatus | null>(null);

  async function poll() {
    const id = eventId.trim();
    if (!id) {
      setLog(t('messages.statusInvalidEventId'));
      setStatus(null);
      return;
    }
    setBusy(true);
    setLog(t('messages.statusRunning', { path: apiLinks.hubEventStatus(id) }));
    try {
      const next = await fetchIngestStatus(id);
      setStatus(next);
      setLog(
        t('messages.statusResult', {
          eventId: next.eventId,
          status: next.status,
          attempts: next.attemptCount,
        }) + (next.lastError ? `\nlastError=${next.lastError}` : ''),
      );
    } catch (err) {
      setStatus(null);
      setLog(err instanceof Error ? err.message : t('common.statusFailed'));
    } finally {
      setBusy(false);
    }
  }

  return { eventId, setEventId, busy, log, status, poll };
}
