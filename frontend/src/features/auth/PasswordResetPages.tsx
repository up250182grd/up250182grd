import { Link, useSearchParams } from 'react-router';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { z } from 'zod';
import { api, ApiError, unwrap } from '@/api/client';
import { AuthLayout } from '@/components/layout/AuthLayout';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { emailSchema, errorMessage, passwordSchema } from './schemas';

const requestSchema = z.object({ email: emailSchema });

export function ForgotPasswordPage() {
  const { register, handleSubmit, formState } = useForm<z.infer<typeof requestSchema>>({
    resolver: zodResolver(requestSchema),
  });
  const mutation = useMutation({
    mutationFn: (body: { email: string }) => unwrap(api.POST('/api/v1/auth/password-reset/request', { body })),
  });

  return (
    <AuthLayout title="Recupera tu contraseña" subtitle="Te enviaremos un enlace para crear una nueva.">
      {mutation.isSuccess ? (
        <Alert tone="success" title="Revisa tu correo">
          Si existe una cuenta con ese correo, recibirás un enlace válido por 1 hora.
        </Alert>
      ) : (
        <form noValidate onSubmit={handleSubmit((v) => mutation.mutate(v))} className="flex flex-col gap-4">
          {mutation.isError && <Alert tone="error">{errorMessage(mutation.error)}</Alert>}
          <TextField
            label="Correo electrónico"
            type="email"
            autoComplete="email"
            error={formState.errors.email?.message}
            {...register('email')}
          />
          <Button type="submit" loading={mutation.isPending} fullWidth>
            Enviar enlace
          </Button>
        </form>
      )}
      <p className="mt-4 text-center text-sm">
        <Link to="/login" className="text-primary underline-offset-4 hover:underline">
          Volver a iniciar sesión
        </Link>
      </p>
    </AuthLayout>
  );
}

const resetSchema = z
  .object({ newPassword: passwordSchema, confirm: z.string() })
  .refine((v) => v.newPassword === v.confirm, { path: ['confirm'], message: 'Las contraseñas no coinciden.' });

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const { register, handleSubmit, formState, setError } = useForm<z.infer<typeof resetSchema>>({
    resolver: zodResolver(resetSchema),
  });
  const mutation = useMutation({
    mutationFn: (newPassword: string) =>
      unwrap(api.POST('/api/v1/auth/password-reset/confirm', { body: { token, newPassword } })),
    onError: (e) => {
      if (e instanceof ApiError && e.code === 'WEAK_PASSWORD') setError('newPassword', { message: e.message });
    },
  });

  return (
    <AuthLayout title="Nueva contraseña">
      {!token && <Alert tone="error">El enlace no es válido.</Alert>}
      {mutation.isSuccess ? (
        <div className="flex flex-col gap-4">
          <Alert tone="success" title="Contraseña actualizada">
            Por seguridad cerramos tus otras sesiones abiertas.
          </Alert>
          <Link to="/login" className="font-semibold text-primary underline-offset-4 hover:underline">
            Iniciar sesión
          </Link>
        </div>
      ) : (
        token && (
          <form
            noValidate
            onSubmit={handleSubmit((v) => mutation.mutate(v.newPassword))}
            className="flex flex-col gap-4"
          >
            {mutation.isError && !(mutation.error instanceof ApiError && mutation.error.code === 'WEAK_PASSWORD') && (
              <Alert tone="error">{errorMessage(mutation.error)}</Alert>
            )}
            <TextField
              label="Nueva contraseña"
              type="password"
              autoComplete="new-password"
              hint="Mínimo 12 caracteres."
              error={formState.errors.newPassword?.message}
              {...register('newPassword')}
            />
            <TextField
              label="Repite la contraseña"
              type="password"
              autoComplete="new-password"
              error={formState.errors.confirm?.message}
              {...register('confirm')}
            />
            <Button type="submit" loading={mutation.isPending} fullWidth>
              Guardar contraseña
            </Button>
          </form>
        )
      )}
    </AuthLayout>
  );
}
