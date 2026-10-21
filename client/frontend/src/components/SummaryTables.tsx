import { useTranslation } from 'react-i18next';
import type { AttackerStats, PathStats, SummaryResponse } from '../api/types';
import { Card } from './ui/Card';
import { DataTable } from './ui/DataTable';

type Props = {
  summary: SummaryResponse;
};

export function SummaryTables({ summary }: Props) {
  const { t } = useTranslation();
  const byCategory = Object.entries(summary.byCategory ?? {}).map(([key, val]) => ({
    key,
    ...val,
  }));
  const byAction = Object.entries(summary.byAction ?? {}).map(([key, count]) => ({ key, count }));
  const attackers = summary.topAttackers ?? [];
  const paths = summary.topTargetedPaths ?? [];

  return (
    <>
      <Card title={t('stats.summary')} ariaLabel={t('stats.summaryAria')}>
        <div className="metric">{summary.totalEvents}</div>
        <div className="metric-label">{t('stats.totalEvents')}</div>
      </Card>

      <div className="grid-2">
        <Card title={t('stats.byCategory')}>
          <DataTable
            rows={byCategory}
            rowKey={(r) => r.key}
            emptyLabel={t('common.noData')}
            columns={[
              { key: 'cat', header: t('stats.colCategory'), cell: (r) => r.key },
              { key: 'count', header: t('stats.colCount'), numeric: true, cell: (r) => r.count },
              {
                key: 'avg',
                header: t('stats.colAvgThreat'),
                numeric: true,
                cell: (r) => r.avgThreatScore,
              },
            ]}
          />
        </Card>

        <Card title={t('stats.byAction')}>
          <DataTable
            rows={byAction}
            rowKey={(r) => r.key}
            emptyLabel={t('common.noData')}
            columns={[
              { key: 'action', header: t('stats.colAction'), cell: (r) => r.key },
              { key: 'count', header: t('stats.colCount'), numeric: true, cell: (r) => r.count },
            ]}
          />
        </Card>

        <Card title={t('stats.topAttackers')}>
          <DataTable<AttackerStats>
            rows={attackers}
            rowKey={(r) => r.clientIp}
            emptyLabel={t('common.noData')}
            columns={[
              { key: 'ip', header: t('stats.colClientIp'), cell: (r) => r.clientIp },
              { key: 'count', header: t('stats.colCount'), numeric: true, cell: (r) => r.count },
              {
                key: 'avg',
                header: t('stats.colAvgThreat'),
                numeric: true,
                cell: (r) => r.avgThreatScore,
              },
            ]}
          />
        </Card>

        <Card title={t('stats.topPaths')}>
          <DataTable<PathStats>
            rows={paths}
            rowKey={(r) => r.path}
            emptyLabel={t('common.noData')}
            columns={[
              { key: 'path', header: t('stats.colPath'), cell: (r) => r.path },
              { key: 'count', header: t('stats.colCount'), numeric: true, cell: (r) => r.count },
            ]}
          />
        </Card>
      </div>
    </>
  );
}
