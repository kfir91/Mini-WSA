import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { seedEvents } from '../api/hubApi';
import { appConfig } from '../config/appConfig';

export function useSeedEvents() {
  const { t } = useTranslation();
  const [count, setCount] = useState<number>(appConfig.defaults.seedEventCount);
  const [configId, setConfigId] = useState<string>(appConfig.defaults.configId);
  const [busy, setBusy] = useState(false);
  const [log, setLog] = useState('');

  async function run() {
    const n = Number(count);
    if (!Number.isFinite(n) || n < 1 || n > appConfig.defaults.seedEventCountMax) {
      setLog(
        t('messages.seedInvalidCount', {
          min: 1,
          max: appConfig.defaults.seedEventCountMax,
        }),
      );
      return;
    }
    const trimmedConfig = configId.trim();
    const parsedConfig = trimmedConfig ? Number(trimmedConfig) : undefined;
    if (parsedConfig !== undefined && !Number.isFinite(parsedConfig)) {
      setLog(t('messages.seedInvalidConfigId'));
      return;
    }
    setBusy(true);
    setLog(
      t('messages.seedRunning', {
        count: n,
        configId: trimmedConfig || appConfig.defaults.configId,
        path: appConfig.paths.hubSeedEvents,
      }),
    );
    try {
      const result = await seedEvents({
        count: n,
        configId: parsedConfig,
      });
      setLog(
        `${result.message}\n` +
          t('messages.seedResult', {
            accepted: result.accepted,
            failed: result.failed,
            configId: result.configId,
          }),
      );
    } catch (err) {
      setLog(err instanceof Error ? err.message : t('common.seedFailed'));
    } finally {
      setBusy(false);
    }
  }

  return { count, setCount, configId, setConfigId, busy, log, run };
}
