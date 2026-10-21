import { useTranslation } from 'react-i18next';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { DataTable } from '../components/ui/DataTable';
import { LogPanel } from '../components/ui/LogPanel';
import { SectionHead } from '../components/ui/SectionHead';
import { appConfig } from '../config/appConfig';
import { useAlertsBoard } from '../hooks/useAlertsBoard';
import type { AlertEvalRow } from '../api/types';

export function AlertsPage() {
  const { t } = useTranslation();
  const { rows, log, firingCount, refresh, seedAll, fireDemo } = useAlertsBoard();

  return (
    <section className="stack">
      <SectionHead title={t('hub.alertsTitle')}>
        <code>POST {appConfig.paths.alertsDefine}</code> ·{' '}
        <code>GET {appConfig.paths.alertsEvaluate}</code>
      </SectionHead>

      <div className="toolbar">
        <Button variant="primary" onClick={() => void seedAll()}>
          {t('hub.createAllRules')}
        </Button>
        <Button onClick={() => void fireDemo()}>{t('hub.makeFiring')}</Button>
        <Button onClick={() => void refresh()}>{t('hub.refreshEvals')}</Button>
      </div>

      <Card>
        <p className="meta">
          {t('hub.alertBoardMeta', {
            rules: rows.length,
            firing: firingCount,
            quiet: rows.length - firingCount,
          })}
        </p>
        {rows.length > 0 && (
          <DataTable<AlertEvalRow>
            rows={rows}
            rowKey={(r) => r.alertId}
            emptyLabel={t('common.noData')}
            columns={[
              {
                key: 'rule',
                header: t('hub.colRule'),
                cell: (r) => r.name ?? r.alertId,
              },
              {
                key: 'status',
                header: t('hub.colStatus'),
                cell: (r) => (r.firing ? t('common.firing') : t('common.ok')),
              },
              {
                key: 'value',
                header: t('hub.colValue'),
                numeric: true,
                cell: (r) => r.value,
              },
              {
                key: 'threshold',
                header: t('hub.colThreshold'),
                numeric: true,
                cell: (r) => r.threshold,
              },
            ]}
          />
        )}
      </Card>

      <LogPanel text={log} />
    </section>
  );
}
