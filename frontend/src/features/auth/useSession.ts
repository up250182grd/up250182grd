import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, api, logoutRequest, unwrap, type Schemas } from '@/api/client';

export type Me = Schemas['MeView'];

export const meQueryKey = ['me'] as const;

/** Usuario actual o `null` si no hay sesión. Fuente de verdad: el backend (cookie de sesión HttpOnly). */
export function useMe() {
  return useQuery({
    queryKey: meQueryKey,
    queryFn: async (): Promise<Me | null> => {
      try {
        return await unwrap(api.GET('/api/v1/me'));
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) return null;
        throw e;
      }
    },
    staleTime: 60_000,
    retry: (count, e) => !(e instanceof ApiError && e.status < 500) && count < 2,
  });
}

export function isAdmin(me: Me | null | undefined): boolean {
  return !!me?.roles?.includes('ADMIN');
}

export function useLogin() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Schemas['LoginRequest']) => unwrap(api.POST('/api/v1/auth/login', { body })),
    onSuccess: (me) => qc.setQueryData(meQueryKey, me),
  });
}

export function useLogout() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: logoutRequest,
    onSettled: () => {
      qc.setQueryData(meQueryKey, null);
      qc.removeQueries({ predicate: (q) => q.queryKey[0] !== 'me' });
    },
  });
}
