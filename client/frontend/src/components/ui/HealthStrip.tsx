import { useTranslation } from 'react-i18next';
import { appConfig, type ServiceId } from '../../config/appConfig';
import type { ProbeState } from '../../hooks/useServiceHealth';

type Props = {
  probes: Partial<Record<ServiceId, ProbeState>>;
};

export function HealthStrip({ probes }: Props) {
  const { t } = useTranslation();

  return (
    <div className="health-strip" aria-label={t('hub.healthAria')}>
      {appConfig.healthTargets.map((id) => (
        <span key={id} className={`pill ${probes[id] ?? ''}`}>
          <span className="dot" />
          {t(`services.${id}`)}
        </span>
      ))}
    </div>
  );
}
