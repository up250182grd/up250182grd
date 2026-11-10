import { Link, useLocation, useNavigate } from 'react-router';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { AuthLayout } from '@/components/layout/AuthLayout';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { errorMessage, loginSchema, type LoginForm } from './schemas';
import { useLogin } from './useSession';

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/app';
  const login = useLogin();
  const { register, handleSubmit, formState } = useForm<LoginForm>({ resolver: zodResolver(loginSchema) });

  const onSubmit = handleSubmit((values) =>
    login.mutate(values, { onSuccess: () => navigate(from.startsWith('/') ? from : '/app', { replace: true }) }),
  );

  return (
    <AuthLayout title="Inicia sesión" subtitle="Continúa donde te quedaste.">
      <form noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
        {login.isError && <Alert tone="error">{errorMessage(login.error)}</Alert>}
        <TextField
          label="Correo electrónico"
          type="email"
          autoComplete="email"
          inputMode="email"
          error={formState.errors.email?.message}
          {...register('email')}
        />
        <TextField
          label="Contraseña"
          type="password"
          autoComplete="current-password"
          error={formState.errors.password?.message}
          {...register('password')}
        />
        <div className="-mt-1 text-right text-sm">
          <Link to="/forgot-password" className="text-primary underline-offset-4 hover:underline">
            ¿Olvidaste tu contraseña?
          </Link>
        </div>
        <Button type="submit" loading={login.isPending} fullWidth>
          Entrar
        </Button>
        <p className="text-center text-sm text-muted">
          ¿Aún no tienes cuenta?{' '}
          <Link to="/register" className="font-semibold text-primary underline-offset-4 hover:underline">
            Regístrate
          </Link>
        </p>
      </form>
    </AuthLayout>
  );
}
