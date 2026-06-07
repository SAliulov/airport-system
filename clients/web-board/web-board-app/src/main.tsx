import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { AirportConfigProvider } from '../../../shared/context/AirportConfigProvider';
import { API_BASE } from './config';
import './index.css';
import App from './App.tsx';

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
    <AirportConfigProvider apiBase={API_BASE} devFallback={devFallback}>
      <App />
    </AirportConfigProvider>
  </StrictMode>,
);
