import { StrictMode, type ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import { ApolloProvider } from '@apollo/client';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import '@fontsource-variable/manrope';
import { apolloClient } from './apollo';
import { getToken } from './auth';
import AppShell from './components/AppShell';
import BeanstalkPage from './pages/BeanstalkPage';
import Expenses from './pages/Expenses';
import Forecast from './pages/Forecast';
import Home from './pages/Home';
import Loans from './pages/Loans';
import Login from './pages/Login';
import './index.css';

function RequireAuth({ children }: { children: ReactNode }) {
  return getToken() ? children : <Navigate to="/login" replace />;
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ApolloProvider client={apolloClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route
            element={
              <RequireAuth>
                <AppShell />
              </RequireAuth>
            }
          >
            <Route index element={<Home />} />
            <Route path="forecast" element={<Forecast />} />
            <Route path="loans" element={<Loans />} />
            <Route path="beanstalk" element={<BeanstalkPage />} />
            <Route path="expenses" element={<Expenses />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </ApolloProvider>
  </StrictMode>,
);
