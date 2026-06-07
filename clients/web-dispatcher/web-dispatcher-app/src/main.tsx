import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { AirportConfigProvider } from '../../../shared/context/AirportConfigProvider';
import { AuthProvider } from './context/AuthContext';
import App from './App.tsx';
import { API_BASE } from './config';
import './index.css';

const devFallback =
  import.meta.env.DEV &&
  import.meta.env.VITE_AIRPORT_HOME_IATA &&
  import.meta.env.VITE_AIRPORT_TIMEZONE
    ? {
        homeIata: String(import.meta.env.VITE_AIRPORT_HOME_IATA),
        timezone: String(import.meta.env.VITE_AIRPORT_TIMEZONE),
      }
    : undefined;

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter basename="/dispatcher">
      <AirportConfigProvider apiBase={API_BASE} devFallback={devFallback}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </AirportConfigProvider>
    </BrowserRouter>
  </StrictMode>,
);
