import { lazy, Suspense } from 'react';
import { createBrowserRouter, Link } from 'react-router';
import { AppShell } from '@/components/layout/AppShell';
import { RedirectIfAuthenticated, RequireAdmin, RequireAuth } from '@/features/auth/guards';
import { LoginPage } from '@/features/auth/LoginPage';
import { ForgotPasswordPage, ResetPasswordPage } from '@/features/auth/PasswordResetPages';
import { RegisterPage } from '@/features/auth/RegisterPage';
import { VerifyEmailPage } from '@/features/auth/VerifyEmailPage';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { LandingPage } from '@/features/landing/LandingPage';
import { SettingsPage } from '@/features/settings/SettingsPage';

// El código del panel de administración se descarga solo cuando un administrador lo abre.
const AdminUsersPage = lazy(() =>
  import('@/features/admin/AdminUsersPage').then((m) => ({ default: m.AdminUsersPage })),
);

function NotFoundPage() {
  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col items-center justify-center gap-3 p-6 text-center">
      <p className="text-5xl font-bold text-primary">404</p>
      <h1 className="text-xl font-semibold">Página no encontrada</h1>
      <Link to="/" className="text-primary underline-offset-4 hover:underline">
        Volver al inicio
      </Link>
    </main>
  );
}

export const router = createBrowserRouter([
  { path: '/', element: <LandingPage /> },
  { path: '/login', element: <RedirectIfAuthenticated><LoginPage /></RedirectIfAuthenticated> },
  { path: '/register', element: <RedirectIfAuthenticated><RegisterPage /></RedirectIfAuthenticated> },
  { path: '/verify-email', element: <VerifyEmailPage /> },
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  { path: '/reset-password', element: <ResetPasswordPage /> },
  {
    element: (
      <RequireAuth>
        <AppShell />
      </RequireAuth>
    ),
    children: [
      { path: '/app', element: <DashboardPage /> },
      { path: '/app/settings', element: <SettingsPage /> },
      {
        path: '/admin/users',
        element: (
          <RequireAdmin>
            <Suspense fallback={<p className="p-6 text-muted">Cargando…</p>}>
              <AdminUsersPage />
            </Suspense>
          </RequireAdmin>
        ),
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
]);
