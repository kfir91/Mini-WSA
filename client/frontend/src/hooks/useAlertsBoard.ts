import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  defineAlert,
  evaluateAlerts,
  fireDemoAlerts,
  loadDemoAlertRules,
} from '../api/analyticsApi';
import type { AlertEvalRow, DemoAlertRule } from '../api/types';
import { appConfig } from '../config/appConfig';

export function useAlertsBoard() {
  const { t } = useTranslation();
  const [demoRules, setDemoRules] = useState<DemoAlertRule[]>([]);
  const [rows, setRows] = useState<AlertEvalRow[]>([]);
  const [log, setLog] = useState('');

  useEffect(() => {
    void loadDemoAlertRules()
      .then(setDemoRules)
      .catch(() =>
        setLog(t('messages.alertRulesLoadFailed', { path: appConfig.paths.alertRules })),
      );
  }, [t]);

  const firingCount = useMemo(() => rows.filter((r) => r.firing).length, [rows]);

  const refresh = useCallback(async () => {
    setLog(t('messages.loadingEvaluate', { path: appConfig.paths.alertsEvaluate }));
    try {
      const next = await evaluateAlerts();
      setRows(next);
      setLog(t('messages.loadedEvals', { count: next.length }));
    } catch (err) {
      setLog(err instanceof Error ? err.message : t('common.evaluateFailed'));
    }
  }, [t]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function seedAll() {
    setLog(t('messages.creatingRules', { count: demoRules.length }));
    const created: string[] = [];
    const failed: string[] = [];
    for (const demo of demoRules) {
      try {
        const res = await defineAlert(demo.rule);
        created.push(`${res.id} ← ${demo.label}`);
      } catch (err) {
        failed.push(`${demo.label}: ${err instanceof Error ? err.message : String(err)}`);
      }
    }
    setLog(
      t('messages.createdRules', { created: created.length, total: demoRules.length }) +
        (failed.length ? `\n${t('messages.failedHeader')}\n${failed.join('\n')}` : '') +
        `\n\n${created.join('\n')}`,
    );
    await refresh();
  }

  async function fireDemo() {
    try {
      const body = await fireDemoAlerts();
      setLog(JSON.stringify(body, null, 2));
      await refresh();
    } catch (err) {
      setLog(err instanceof Error ? err.message : t('common.demoFireFailed'));
    }
  }

  return { rows, log, firingCount, refresh, seedAll, fireDemo };
}
