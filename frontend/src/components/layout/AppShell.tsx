import { NavLink, Outlet, useNavigate } from 'react-router';
import {
  BarChart3,
  BookOpen,
  GraduationCap,
  Home,
  Library,
  LogOut,
  MessagesSquare,
  Settings,
  Shield,
  Users,
  Wallet,
  type LucideIcon,
} from 'lucide-react';
import { Logo } from '@/components/ui/Logo';
import { ThemeToggle } from '@/components/ui/ThemeToggle';
import { cn } from '@/lib/cn';
import { LangProvider } from '@/lib/lang';
import { isAdmin, useLogout, useMe } from '@/features/auth/useSession';

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  /** Pantalla aún no construida: se muestra deshabilitada y con su fase, sin simular funcionalidad. */
  phase?: string;
}

const studentNav: NavItem[] = [
  { to: '/app', label: 'Inicio', icon: Home },
  { to: '/app/plan', label: 'Mi plan', icon: GraduationCap },
  { to: '/app/practice', label: 'Repaso', icon: BookOpen, phase: 'Fase 6' },
  { to: '/app/tutor', label: 'Profesor IA', icon: MessagesSquare },
  { to: '/app/wallet', label: 'Billetera', icon: Wallet },
  { to: '/app/settings', label: 'Ajustes', icon: Settings },
];

const adminNav: NavItem[] = [
  { to: '/admin/users', label: 'Usuarios', icon: Users },
  { to: '/admin/content', label: 'Contenido', icon: Library },
  { to: '/admin/billing', label: 'Finanzas', icon: BarChart3 },
];

function SideLink({ item }: { item: NavItem }) {
  const Icon = item.icon;
  if (item.phase) {
    return (
      <span
        aria-disabled="true"
        className="flex min-h-11 items-center gap-3 rounded-lg px-3 text-sm text-muted/70"
        title={`Disponible en ${item.phase}`}
      >
        <Icon aria-hidden className="size-5" />
        <span className="flex-1">{item.label}</span>
        <span className="rounded bg-surface-2 px-1.5 py-0.5 text-[10px] font-semibold uppercase">
          {item.phase}
        </span>
      </span>
    );
  }
  return (
    <NavLink
      to={item.to}
      end={item.to === '/app'}
      className={({ isActive }) =>
        cn(
          'flex min-h-11 items-center gap-3 rounded-lg px-3 text-sm font-medium transition-colors',
          isActive ? 'bg-primary/12 text-primary' : 'text-text hover:bg-surface-2',
        )
      }
    >
      <Icon aria-hidden className="size-5" />
      {item.label}
    </NavLink>
  );
}

/** Estructura principal: sidebar en escritorio, barra inferior en móvil (docs §8.2). */
export function AppShell() {
  const { data: me } = useMe();
  const logout = useLogout();
  const navigate = useNavigate();
  const admin = isAdmin(me);

  const onLogout = async () => {
    await logout.mutateAsync().catch(() => undefined);
    navigate('/login', { replace: true });
  };

  // Barra inferior móvil: máximo 5 destinos (el resto sigue en el menú de escritorio y en enlaces del contenido).
  const mobileNav = admin
    ? [studentNav[0]!, studentNav[1]!, studentNav[3]!, adminNav[2]!, adminNav[1]!]
    : studentNav.filter((i) => !i.phase);

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[260px_1fr]">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-surface focus:p-2"
      >
        Saltar al contenido
      </a>

      <aside className="hidden border-r border-border bg-surface lg:flex lg:flex-col">
        <div className="px-5 py-4">
          <Logo />
        </div>
        <nav aria-label="Principal" className="flex flex-1 flex-col gap-1 px-3">
          {studentNav.map((item) => (
            <SideLink key={item.to} item={item} />
          ))}
          {admin && (
            <>
              <p className="mt-6 flex items-center gap-2 px-3 pb-1 text-xs font-semibold uppercase tracking-wide text-muted">
                <Shield aria-hidden className="size-3.5" /> Administración
              </p>
              {adminNav.map((item) => (
                <SideLink key={item.to} item={item} />
              ))}
            </>
          )}
        </nav>
        <div className="flex items-center justify-between border-t border-border px-3 py-3">
          <ThemeToggle />
          <button
            type="button"
            onClick={onLogout}
            className="inline-flex min-h-11 items-center gap-2 rounded-lg px-3 text-sm text-muted hover:bg-surface-2 hover:text-text"
          >
            <LogOut aria-hidden className="size-4" /> Cerrar sesión
          </button>
        </div>
      </aside>

      <div className="flex min-h-dvh flex-col">
        <header className="flex items-center justify-between border-b border-border bg-surface px-4 py-2 lg:hidden">
          <Logo />
          <div className="flex items-center gap-1">
            <ThemeToggle />
            <button
              type="button"
              onClick={onLogout}
              aria-label="Cerrar sesión"
              className="inline-flex size-11 items-center justify-center rounded-lg text-muted hover:bg-surface-2"
            >
              <LogOut aria-hidden className="size-5" />
            </button>
          </div>
        </header>

        <main id="main" className="flex-1 px-4 pb-24 pt-6 sm:px-6 lg:px-10 lg:pb-10">
          <LangProvider profileLang={me?.profile?.explanationLanguage}>
            <Outlet />
          </LangProvider>
        </main>

        <nav
          aria-label="Principal"
          className="fixed inset-x-0 bottom-0 z-10 flex border-t border-border bg-surface pb-[env(safe-area-inset-bottom)] lg:hidden"
        >
          {mobileNav.slice(0, 5).map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/app'}
                className={({ isActive }) =>
                  cn(
                    'flex min-h-14 flex-1 flex-col items-center justify-center gap-0.5 text-[11px] font-medium',
                    isActive ? 'text-primary' : 'text-muted',
                  )
                }
              >
                <Icon aria-hidden className="size-5" />
                {item.label}
              </NavLink>
            );
          })}
        </nav>
      </div>
    </div>
  );
}
