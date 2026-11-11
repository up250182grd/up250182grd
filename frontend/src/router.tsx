import { lazy, Suspense, type ReactNode } from 'react';
import { createBrowserRouter, Link } from 'react-router';
import { AppShell } from '@/components/layout/AppShell';
import { DiagnosticPage } from '@/features/assessment/DiagnosticPage';
import { RedirectIfAuthenticated, RequireAdmin, RequireAuth } from '@/features/auth/guards';
import { LoginPage } from '@/features/auth/LoginPage';
import { ForgotPasswordPage, ResetPasswordPage } from '@/features/auth/PasswordResetPages';
import { RegisterPage } from '@/features/auth/RegisterPage';
import { VerifyEmailPage } from '@/features/auth/VerifyEmailPage';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { LandingPage } from '@/features/landing/LandingPage';
import { LessonPage } from '@/features/learning/LessonPage';
import { PlanPage } from '@/features/learning/PlanPage';
import { SettingsPage } from '@/features/settings/SettingsPage';
import { ChatPage } from '@/features/tutor/ChatPage';
import { TutorHomePage } from '@/features/tutor/TutorHomePage';
import { WalletPage } from '@/features/wallet/WalletPage';
import { VoicePage } from '@/features/voice/VoicePage';
import { VoiceSessionPage } from '@/features/voice/VoiceSessionPage';
import { PracticePage } from '@/features/practice/PracticePage';
import { VocabularyPage } from '@/features/practice/VocabularyPage';
import { RecommendationWeightsPage } from '@/features/admin/recommendations/RecommendationWeightsPage';

// El código del panel de administración se descarga solo cuando un administrador lo abre.
const AdminUsersPage = lazy(() =>
  import('@/features/admin/AdminUsersPage').then((m) => ({ default: m.AdminUsersPage })),
);
const AdminContentPage = lazy(() =>
  import('@/features/admin/content/AdminContentPage').then((m) => ({ default: m.AdminContentPage })),
);
const AdminBillingPage = lazy(() =>
  import('@/features/admin/billing/AdminBillingPage').then((m) => ({ default: m.AdminBillingPage })),
);
const AdminLessonEditor = lazy(() =>
  import('@/features/admin/content/AdminLessonEditor').then((m) => ({ default: m.AdminLessonEditor })),
);

function AdminRoute({ children }: { children: ReactNode }) {
  return (
    <RequireAdmin>
      <Suspense fallback={<p className="p-6 text-muted">Cargando…</p>}>{children}</Suspense>
    </RequireAdmin>
  );
}

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
      { path: '/app/plan', element: <PlanPage /> },
      { path: '/app/lessons/:lessonId', element: <LessonPage /> },
      { path: '/app/diagnostic', element: <DiagnosticPage /> },
      { path: '/app/settings', element: <SettingsPage /> },
      { path: '/app/tutor', element: <TutorHomePage /> },
      { path: '/app/tutor/:conversationId', element: <ChatPage /> },
      { path: '/app/voice', element: <VoicePage /> },
      { path: '/app/voice/:sessionId', element: <VoiceSessionPage /> },
      { path: '/app/practice', element: <PracticePage /> },
      { path: '/app/vocabulary', element: <VocabularyPage /> },
      { path: '/app/wallet', element: <WalletPage /> },
      { path: '/admin/billing', element: <AdminRoute><AdminBillingPage /></AdminRoute> },
      { path: '/admin/recommendations', element: <AdminRoute><RecommendationWeightsPage /></AdminRoute> },
      { path: '/admin/users', element: <AdminRoute><AdminUsersPage /></AdminRoute> },
      { path: '/admin/content', element: <AdminRoute><AdminContentPage /></AdminRoute> },
      { path: '/admin/content/lessons/:lessonId', element: <AdminRoute><AdminLessonEditor /></AdminRoute> },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
]);
