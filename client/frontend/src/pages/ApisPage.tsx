import { useTranslation } from 'react-i18next';
import { SectionHead } from '../components/ui/SectionHead';
import { apiLinks, appConfig } from '../config/appConfig';

export function ApisPage() {
  const { t } = useTranslation();

  const items = [
    {
      href: apiLinks.summary(),
      label: `GET ${appConfig.paths.statsSummary}`,
      meta: t('hub.apisAllConfigs'),
    },
    {
      href: apiLinks.summaryForConfig(),
      label: apiLinks.summaryForConfig().replace(/^\//, ''),
    },
    {
      href: apiLinks.timeseries(),
      label: apiLinks.timeseries().replace(/^\//, ''),
    },
    {
      href: apiLinks.samples(),
      label: `GET ${apiLinks.samples()}`,
    },
  ];

  return (
    <section className="stack">
      <SectionHead title={t('hub.apisTitle')}>{t('hub.apisBody')}</SectionHead>
      <ul className="link-list">
        {items.map((item) => (
          <li key={item.href}>
            <a href={item.href}>{item.label}</a>
            {item.meta ? <span className="meta">{item.meta}</span> : null}
          </li>
        ))}
      </ul>
    </section>
  );
}
