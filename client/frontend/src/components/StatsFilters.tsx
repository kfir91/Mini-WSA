import { useTranslation } from 'react-i18next';
import { appConfig } from '../config/appConfig';
import type { StatsFilters as Filters } from '../hooks/useStats';
import { Button } from './ui/Button';
import { Card } from './ui/Card';

type Props = {
  filters: Filters;
  loading: boolean;
  onChange: (next: Filters) => void;
  onLoad: () => void;
};

export function StatsFilters({ filters, loading, onChange, onLoad }: Props) {
  const { t } = useTranslation();
  const refreshSeconds = appConfig.defaults.autoRefreshMs / 1000;

  return (
    <Card title={t('stats.filters')} ariaLabel={t('stats.filtersAria')}>
      <div className="form-row">
        <div className="field">
          <label htmlFor="configId">{t('stats.configId')}</label>
          <input
            id="configId"
            type="number"
            min={1}
            value={filters.configId}
            onChange={(e) => onChange({ ...filters, configId: e.target.value })}
          />
        </div>
        <div className="field">
          <label htmlFor="interval">{t('stats.interval')}</label>
          <select
            id="interval"
            value={filters.interval}
            onChange={(e) =>
              onChange({
                ...filters,
                interval: e.target.value as Filters['interval'],
              })
            }
          >
            {appConfig.defaults.intervals.map((interval) => (
              <option key={interval} value={interval}>
                {interval}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="from">{t('stats.from')}</label>
          <input
            id="from"
            type="datetime-local"
            value={filters.fromLocal}
            onChange={(e) => onChange({ ...filters, fromLocal: e.target.value })}
          />
        </div>
        <div className="field">
          <label htmlFor="to">{t('stats.to')}</label>
          <input
            id="to"
            type="datetime-local"
            value={filters.toLocal}
            onChange={(e) => onChange({ ...filters, toLocal: e.target.value })}
          />
        </div>
        <label className="check">
          <input
            type="checkbox"
            checked={filters.autoRefresh}
            onChange={(e) => onChange({ ...filters, autoRefresh: e.target.checked })}
          />
          {t('stats.autoRefresh', { seconds: refreshSeconds })}
        </label>
        <Button variant="primary" onClick={onLoad} disabled={loading}>
          {t('common.load')}
        </Button>
      </div>
    </Card>
  );
}
