import { useEffect, useRef } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api, unwrap } from '@/api/client';
import { AuthLayout } from '@/components/layout/AuthLayout';
import { Alert } from '@/components/ui/Alert';
import { errorMessage } from './schemas';
import { meQueryKey } from './useSession';

export function VerifyEmailPage() {
  const [params] = useSearchParams();
  const token = params.get('token');
  const qc = useQueryClient();
  const started = useRef(false);

  const verify = useMutation({
    mutationFn: (t: string) => unwrap(api.POST('/api/v1/auth/verify-email', { body: { token: t } })),
    onSuccess: () => qc.invalidateQueries({ queryKey: meQueryKey }),
  });

  useEffect(() => {
    // El token es de un solo uso: se envía exactamente una vez aunque React monte dos veces en desarrollo.
    if (token && !started.current) {
      started.current = true;
      verify.mutate(token);
    }
  }, [token, verify]);

  return (
    <AuthLayout title="Confirmación de correo">
      <div className="flex flex-col gap-4">
        {!token && <Alert tone="error">El enlace no es válido: falta el código de verificación.</Alert>}
        {verify.isPending && <p aria-live="polite">Confirmando tu correo…</p>}
        {verify.isSuccess && (
          <Alert tone="success" title="¡Correo confirmado!">
            Ya puedes usar todas las funciones de tu cuenta.
          </Alert>
        )}
        {verify.isError && <Alert tone="error">{errorMessage(verify.error)}</Alert>}
        <Link to="/app" className="font-semibold text-primary underline-offset-4 hover:underline">
          Ir a EnglishOS
        </Link>
      </div>
    </AuthLayout>
  );
}
