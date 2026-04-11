import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const NAV = [
  { to: '/flights', label: '✈ Рейсы' },
  { to: '/schedules', label: '📋 Расписание' },
  { to: '/airlines', label: '🏢 Авиакомпании' },
  { to: '/aircraft-types', label: '✈ Типы ВС' },
  { to: '/gates', label: '🚪 Гейты' },
  { to: '/timeline', label: '📊 Таймлайн' },
];

export default function Layout() {
  const { username, logout } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="layout">
      <nav className="sidebar">
        <div className="sidebar-logo">✈ Диспетчер</div>
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
          <button onClick={handleLogout} className="btn-ghost btn-sm">Выйти</button>
        </div>
      </nav>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
