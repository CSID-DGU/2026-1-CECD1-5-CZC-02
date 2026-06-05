import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { LoginPage } from './components/LoginPage';
import { DashboardLayout } from './components/DashboardLayout';
import { Dashboard } from './components/Dashboard';
import { SettingsPage } from './components/SettingsPage';
import { SalesmapLoginPage } from './components/SalesmapLoginPage';
import { MessageView } from './components/MessageView';
import { GmailOAuthCallbackPage } from './components/GmailOAuthCallbackPage';
import { ProcessingHistoryPage } from './components/ProcessingHistoryPage';
import { CustomerTimelinePage } from './components/CustomerTimelinePage';

function ProtectedRoute({ children }) {
  const accessToken = localStorage.getItem('accessToken');

  if (!accessToken) {
    return <Navigate to="/login" replace />;
  }

  return children;
}

export default function App() {
  return (
    <BrowserRouter>
      <div className="size-full">
        <Routes>
          <Route path="/" element={<Navigate to="/login" replace />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/salesmap-login" element={
            <ProtectedRoute>
              <SalesmapLoginPage />
            </ProtectedRoute>
          } />
          <Route path="/dashboard" element={
            <ProtectedRoute>
              <DashboardLayout>
                <Dashboard />
              </DashboardLayout>
            </ProtectedRoute>
          } />
          <Route path="/messages/:source" element={
            <ProtectedRoute>
              <DashboardLayout>
                <MessageView />
              </DashboardLayout>
            </ProtectedRoute>
          } />
          <Route path="/settings" element={
            <ProtectedRoute>
              <DashboardLayout>
                <SettingsPage />
              </DashboardLayout>
            </ProtectedRoute>
          } />
          <Route path="/history" element={
            <ProtectedRoute>
              <DashboardLayout>
                <ProcessingHistoryPage />
              </DashboardLayout>
            </ProtectedRoute>
          } />
          <Route path="/customers" element={
            <ProtectedRoute>
              <DashboardLayout>
                <CustomerTimelinePage />
              </DashboardLayout>
            </ProtectedRoute>
          } />
          <Route path="/settings/gmail/callback" element={
            <ProtectedRoute>
              <DashboardLayout>
                <GmailOAuthCallbackPage />
              </DashboardLayout>
            </ProtectedRoute>
          } />
        </Routes>
      </div>
    </BrowserRouter>
  );
}
