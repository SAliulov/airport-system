import axios from 'axios';
import { useAirportConfig } from '../../../../shared/context/AirportConfigProvider';
import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/useAuth';
import { brandingLogoUrl } from '../utils/branding';

function loginErrorMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const status = err.response?.status;
    if (status === 401) return 'Неверный логин или пароль';
    if (status === 403) return 'Недостаточно прав для выполнения операции';
    const backend = err.response?.data;
    if (backend && typeof backend === 'object' && 'error' in backend) {
      const msg = (backend as { error?: string }).error;
      if (msg) return msg;
    }
  }
  if (err instanceof Error) return err.message;
  return 'Ошибка входа';
}

export default function LoginPage() {
  const { homeIata } = useAirportConfig();
  const { login } = useAuth();
  const navigate = useNavigate();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handle(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(username, password);
      navigate('/', { replace: true });
    } catch (err: unknown) {
      setError(loginErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-wrapper">
      <form className="login-card" onSubmit={handle}>
        <div className="login-logo">
          <img src={brandingLogoUrl()} alt="АСУРР" />
        </div>
        <h2>АСУРР</h2>
        <p className="login-sub">Диспетчерская служба аэропорта {homeIata}</p>
        <label>
          Логин
          <input
            value={username}
            onChange={e => setUsername(e.target.value)}
            autoFocus
            required
          />
        </label>
        <label>
          Пароль
          <input
            type="password"
            value={password}
            onChange={e => setPassword(e.target.value)}
            required
          />
        </label>
        {error && <p className="login-error">{error}</p>}
        <button type="submit" disabled={loading} className="btn-primary">
          {loading ? 'Вход…' : 'Войти'}
        </button>
      </form>
    </div>
  );
}
