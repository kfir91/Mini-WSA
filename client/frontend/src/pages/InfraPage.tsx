import { useTranslation } from 'react-i18next';
import { Card } from '../components/ui/Card';
import { SectionHead } from '../components/ui/SectionHead';
import { apiLinks, servicePortLabel } from '../config/appConfig';

export function InfraPage() {
  const { t } = useTranslation();

  return (
    <section className="stack">
      <SectionHead title={t('hub.infraTitle')}>{t('hub.infraBody')}</SectionHead>
      <div className="grid">
        <Card title={t('hub.adminer')}>
          <span className="port-badge">{servicePortLabel('adminer')}</span>
          <div className="card-actions">
            <a href={apiLinks.adminerConnect()}>{t('common.connect')}</a>
          </div>
        </Card>
        <Card title={t('hub.kafkaUi')}>
          <span className="port-badge">{servicePortLabel('kafkaUi')}</span>
          <div className="card-actions">
            <a href={apiLinks.kafkaUi()}>{t('common.open')}</a>
          </div>
        </Card>
      </div>
    </section>
  );
}
