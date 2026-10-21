import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getText } from '../api/analyticsApi';
import { appConfig } from '../config/appConfig';

export function useRateLimitDemo() {
  const { t } = useTranslation();
  const [log, setLog] = useState(() => t('messages.rateIdle'));
  const [busy, setBusy] = useState(false);

  async function hammer(url: string, label: string) {
    setBusy(true);
    setLog(t('messages.rateSending', { label, url }));
    let ok = 0;
    const max = appConfig.defaults.hammerMaxRequests;
    const every = appConfig.defaults.hammerProgressEvery;
    const limit = appConfig.defaults.rateLimitPerIpPerMin;
    try {
      for (let i = 1; i <= max; i += 1) {
        const { status, body: text } = await getText(url);
        if (status === 429) {
          setLog(t('messages.rateHit429', { label, ok, n: i, body: text }));
          return;
        }
        if (status < 200 || status >= 300) {
          setLog(t('messages.rateUnexpected', { status, n: i, body: text }));
          return;
        }
        ok += 1;
        if (i === 1 || i % every === 0) {
          setLog(t('messages.rateProgress', { label, ok, limit }));
        }
      }
      setLog(t('messages.rateNoLimit', { max }));
    } catch (err) {
      setLog(err instanceof Error ? err.message : t('common.hammerFailed'));
    } finally {
      setBusy(false);
    }
  }

  return { log, busy, hammer };
}
