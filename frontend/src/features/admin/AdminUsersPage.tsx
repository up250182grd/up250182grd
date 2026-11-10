import { useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Search } from 'lucide-react';
import { api, unwrap, type Schemas } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card, Skeleton } from '@/components/ui/Card';
import { errorMessage } from '@/features/auth/schemas';
import { useMe } from '@/features/auth/useSession';

type AdminUser = Schemas['AdminUserView'];
type Status = 'ACTIVE' | 'SUSPENDED' | 'DELETED';

const statusLabel: Record<string, string> = { ACTIVE: 'Activa', SUSPENDED: 'Suspendida', DELETED: 'Eliminada' };
const dateFmt = new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' });

function UserRow({ user, isSelf }: { user: AdminUser; isSelf: boolean }) {
  const qc = useQueryClient();
  const suspended = user.status === 'SUSPENDED';
  const action = useMutation({
    mutationFn: () =>
      unwrap(
        api.POST(suspended ? '/api/v1/admin/users/{publicId}/reactivate' : '/api/v1/admin/users/{publicId}/suspend', {
          params: { path: { publicId: user.publicId ?? '' } },
        }),
      ),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'users'] }),
  });

  const onClick = () => {
    const verb = suspended ? 'reactivar' : 'suspender';
    // Confirmación explícita para una acción sensible (además queda en la auditoría).
    if (window.confirm(`¿Seguro que quieres ${verb} la cuenta ${user.email}?`)) action.mutate();
  };

  return (
    <tr className="border-t border-border align-top">
      <td className="px-3 py-3">
        <p className="font-medium">{user.displayName}</p>
        <p className="break-all text-sm text-muted">{user.email}</p>
        {action.isError && <p className="mt-1 text-sm text-danger">{errorMessage(action.error)}</p>}
      </td>
      <td className="px-3 py-3 text-sm">{user.roles?.join(', ')}</td>
      <td className="px-3 py-3 text-sm">
        <span className={suspended ? 'font-semibold text-danger' : ''}>{statusLabel[user.status ?? ''] ?? user.status}</span>
        {!user.emailVerified && <span className="block text-xs text-muted">Correo sin verificar</span>}
      </td>
      <td className="hidden px-3 py-3 text-sm text-muted md:table-cell">
        {user.createdAt ? dateFmt.format(new Date(user.createdAt)) : '—'}
      </td>
      <td className="px-3 py-3 text-right">
        {!isSelf && (
          <Button variant={suspended ? 'secondary' : 'danger'} loading={action.isPending} onClick={onClick}>
            {suspended ? 'Reactivar' : 'Suspender'}
          </Button>
        )}
      </td>
    </tr>
  );
}

export function AdminUsersPage() {
  const { data: me } = useMe();
  const [query, setQuery] = useState('');
  const [submitted, setSubmitted] = useState('');
  const [status, setStatus] = useState<Status | ''>('');
  const [page, setPage] = useState(0);

  const users = useQuery({
    queryKey: ['admin', 'users', submitted, status, page],
    queryFn: () =>
      unwrap(
        api.GET('/api/v1/admin/users', {
          params: { query: { q: submitted || undefined, status: status || undefined, page, size: 20 } },
        }),
      ),
    placeholderData: keepPreviousData,
  });

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Usuarios</h1>
        <p className="mt-1 text-muted">Consulta, busca y suspende cuentas. Cada acción queda registrada en la auditoría.</p>
      </div>

      <form
        role="search"
        className="flex flex-col gap-3 sm:flex-row"
        onSubmit={(e) => {
          e.preventDefault();
          setPage(0);
          setSubmitted(query.trim());
        }}
      >
        <label className="sr-only" htmlFor="user-search">
          Buscar por correo
        </label>
        <input
          id="user-search"
          type="search"
          placeholder="Buscar por correo…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="min-h-11 flex-1 rounded-lg border border-border bg-surface px-3"
        />
        <label className="sr-only" htmlFor="status-filter">
          Filtrar por estado
        </label>
        <select
          id="status-filter"
          value={status}
          onChange={(e) => {
            setPage(0);
            setStatus(e.target.value as Status | '');
          }}
          className="min-h-11 rounded-lg border border-border bg-surface px-3"
        >
          <option value="">Todos los estados</option>
          <option value="ACTIVE">Activas</option>
          <option value="SUSPENDED">Suspendidas</option>
        </select>
        <Button type="submit" icon={<Search aria-hidden className="size-4" />}>
          Buscar
        </Button>
      </form>

      {users.isError && <Alert tone="error">{errorMessage(users.error)}</Alert>}

      <Card className="overflow-x-auto p-0">
        {users.isPending ? (
          <div className="flex flex-col gap-3 p-4" aria-busy="true" aria-label="Cargando usuarios">
            <Skeleton className="h-10" />
            <Skeleton className="h-10" />
            <Skeleton className="h-10" />
          </div>
        ) : users.data && (users.data.items?.length ?? 0) === 0 ? (
          <p className="p-6 text-center text-muted">No hay usuarios que coincidan con la búsqueda.</p>
        ) : (
          <table className="w-full text-left">
            <caption className="sr-only">Lista de usuarios</caption>
            <thead className="bg-surface-2 text-xs uppercase tracking-wide text-muted">
              <tr>
                <th scope="col" className="px-3 py-2">Usuario</th>
                <th scope="col" className="px-3 py-2">Roles</th>
                <th scope="col" className="px-3 py-2">Estado</th>
                <th scope="col" className="hidden px-3 py-2 md:table-cell">Alta</th>
                <th scope="col" className="px-3 py-2"><span className="sr-only">Acciones</span></th>
              </tr>
            </thead>
            <tbody>
              {users.data?.items?.map((u) => (
                <UserRow key={u.publicId} user={u} isSelf={u.publicId === me?.publicId} />
              ))}
            </tbody>
          </table>
        )}
      </Card>

      {users.data && (users.data.totalPages ?? 0) > 1 && (
        <nav aria-label="Paginación" className="flex items-center justify-between">
          <Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            Anterior
          </Button>
          <span className="text-sm text-muted">
            Página {page + 1} de {users.data.totalPages}
          </span>
          <Button
            variant="secondary"
            disabled={page + 1 >= (users.data.totalPages ?? 1)}
            onClick={() => setPage((p) => p + 1)}
          >
            Siguiente
          </Button>
        </nav>
      )}
    </div>
  );
}
