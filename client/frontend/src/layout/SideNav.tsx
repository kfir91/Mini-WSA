import { useTranslation } from 'react-i18next';
import { NavLink } from 'react-router-dom';
import { navItems } from '../config/navItems';

export function SideNav() {
  const { t } = useTranslation();

  return (
    <nav className="sidebar" aria-label={t('nav.aria')}>
      <div className="sidebar-label">{t('nav.label')}</div>
      {navItems.map((item) => (
        <NavLink
          key={item.id}
          to={item.path}
          end={item.end}
          className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
        >
          {t(item.labelKey)}
        </NavLink>
      ))}
    </nav>
  );
}
