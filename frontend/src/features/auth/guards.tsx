import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { Skeleton } from '@/components/ui/Card';
import { Alert } from '@/components/ui/Alert';
import { isAdmin, useMe } from './useSession';

function FullPageLoading() {
  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-4 p-8" aria-busy="true" aria-label="Cargando">
      <Skeleton className="h-8 w-1/3" />
      <Skeleton className="h-28 w-full" />
      <Skeleton className="h-28 w-full" />
    </div>
  );
}

/**
 * Protege rutas que requieren sesión. Solo mejora la experiencia: la autorización real la hace el backend
 * en cada solicitud (un usuario no se vuelve administrador por manipular la interfaz).
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { data: me, isPending, isError } = useMe();
  const location = useLocation();
  if (isPending) return <FullPageLoading />;
  if (isError) {
    return (
      <div className="mx-auto max-w-md p-8">
        <Alert tone="error" title="No se pudo cargar tu sesión">
          Revisa tu conexión y recarga la página.
        </Alert>
      </div>
    );
  }
  if (!me) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return children;
}

export function RequireAdmin({ children }: { children: ReactNode }) {
  const { data: me } = useMe();
  if (!isAdmin(me)) return <Navigate to="/app" replace />;
  return children;
}

/** Si ya hay sesión, las pantallas de acceso redirigen al panel. */
export function RedirectIfAuthenticated({ children }: { children: ReactNode }) {
  const { data: me, isPending } = useMe();
  if (isPending) return <FullPageLoading />;
  if (me) return <Navigate to="/app" replace />;
  return children;
}
