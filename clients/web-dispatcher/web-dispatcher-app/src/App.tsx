import React from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import FlightsPage from './pages/FlightsPage';
import SchedulesPage from './pages/SchedulesPage';
import AirlinesPage from './pages/AirlinesPage';
import AircraftTypesPage from './pages/AircraftTypesPage';
import GatesPage from './pages/GatesPage';
import TimelinePage from './pages/TimelinePage';

function RequireAuth({ children }: { children: React.ReactElement }) {
  const { token } = useAuth();
  const location = useLocation();
  if (!token) return <Navigate to="/login" state={{ from: location }} replace />;
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="/flights" replace />} />
        <Route path="flights" element={<FlightsPage />} />
        <Route path="schedules" element={<SchedulesPage />} />
        <Route path="airlines" element={<AirlinesPage />} />
        <Route path="aircraft-types" element={<AircraftTypesPage />} />
        <Route path="gates" element={<GatesPage />} />
        <Route path="timeline" element={<TimelinePage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
