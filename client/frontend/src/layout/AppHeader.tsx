import { useTranslation } from 'react-i18next';
import { apiLinks, appConfig } from '../config/appConfig';
import { HealthStrip } from '../components/ui/HealthStrip';
import { useServiceHealth } from '../hooks/useServiceHealth';

export function AppHeader() {
  const { t } = useTranslation();
  const probes = useServiceHealth();

  return (
    <header className="header">
      <h1 className="brand">
        {t('brand.mini')} <span>{t('brand.wsa')}</span>
      </h1>
      <p className="header-meta">
        {t('hub.topMetaPrefix')}{' '}
        <a href={apiLinks.clientHub()}>
          {t('hub.clientLink', { port: appConfig.ports.client })}
        </a>
      </p>
      <HealthStrip probes={probes} />
    </header>
  );
}
