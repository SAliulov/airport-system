import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/useAuth';
import DispatcherClock from './DispatcherClock';
import { brandingLogoUrl } from '../utils/branding';

const NAV = [
  { to: '/flights', label: 'Рейсы' },
  { to: '/schedules', label: 'Плановое расписание' },
  { to: '/airlines', label: 'Авиакомпании' },
  { to: '/aircraft-types', label: 'Типы ВС' },
  { to: '/gates', label: 'Гейты' },
  { to: '/timeline', label: 'Таймлайн' },
  { to: '/faq', label: 'Справка / FAQ' },
];

export default function Layout() {
  const { username, logout } = useAuth();
  const navigate = useNavigate();
  const [logoutConfirmOpen, setLogoutConfirmOpen] = useState(false);

  async function confirmLogout() {
    setLogoutConfirmOpen(false);
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="layout layout-with-topbar">
      <header className="app-topbar">
        <DispatcherClock />
      </header>
      {logoutConfirmOpen && (
        <div className="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="logout-confirm-title">
          <div className="modal-card">
            <h3 id="logout-confirm-title">Выход</h3>
            <p>Вы уверены, что хотите выйти?</p>
            <div className="modal-actions modal-actions--footer">
              <button type="button" className="btn-ghost" onClick={() => setLogoutConfirmOpen(false)}>
                Отмена
              </button>
              <button type="button" className="btn-primary" onClick={() => void confirmLogout()}>
                Выйти
              </button>
            </div>
          </div>
        </div>
      )}
      <nav className="sidebar">
        <div className="sidebar-brand">
          <img src={brandingLogoUrl()} alt="АСУРР" />
          <span className="sidebar-brand-text">АСУРР<br />Диспетчер</span>
        </div>
        <ul>
          {NAV.map(n => (
            <li key={n.to}>
              <NavLink to={n.to} className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
                {n.label}
              </NavLink>
            </li>
          ))}
        </ul>
        <div className="sidebar-user">
          <span>{username}</span>
          <button type="button" onClick={() => setLogoutConfirmOpen(true)} className="btn-ghost btn-sm">
            Выйти
          </button>
        </div>
      </nav>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
