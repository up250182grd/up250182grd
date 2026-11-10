import { useState } from 'react';
import { Link } from 'react-router';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { MailCheck } from 'lucide-react';
import { api, ApiError, unwrap } from '@/api/client';
import { AuthLayout } from '@/components/layout/AuthLayout';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { applyFieldErrors, errorMessage, registerSchema, type RegisterForm } from './schemas';

export function RegisterPage() {
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null);
  const form = useForm<RegisterForm>({ resolver: zodResolver(registerSchema), mode: 'onBlur' });
  const { register, handleSubmit, formState, setError } = form;

  const mutation = useMutation({
    mutationFn: (values: RegisterForm) => unwrap(api.POST('/api/v1/auth/register', { body: values })),
    onSuccess: (_, values) => setRegisteredEmail(values.email),
    onError: (e) => {
      applyFieldErrors(e, setError);
      if (e instanceof ApiError && e.code === 'WEAK_PASSWORD') setError('password', { message: e.message });
      if (e instanceof ApiError && e.code === 'EMAIL_TAKEN') setError('email', { message: e.message });
    },
  });

  if (registeredEmail) {
    return (
      <AuthLayout title="Revisa tu correo">
        <div className="flex flex-col items-center gap-3 text-center">
          <MailCheck aria-hidden className="size-12 text-primary" />
          <p>
            Enviamos un enlace de confirmación a <strong className="break-all">{registeredEmail}</strong>.
          </p>
          <p className="text-sm text-muted">Puede tardar un par de minutos. Revisa también la carpeta de spam.</p>
          <Link to="/login" className="mt-2 font-semibold text-primary underline-offset-4 hover:underline">
            Ir a iniciar sesión
          </Link>
        </div>
      </AuthLayout>
    );
  }

  const fieldsHandled =
    mutation.error instanceof ApiError &&
    (mutation.error.fieldErrors.length > 0 || ['WEAK_PASSWORD', 'EMAIL_TAKEN'].includes(mutation.error.code));

  return (
    <AuthLayout title="Crea tu cuenta" subtitle="Empieza con un diagnóstico de tu nivel de inglés.">
      <form noValidate onSubmit={handleSubmit((v) => mutation.mutate(v))} className="flex flex-col gap-4">
        {mutation.isError && !fieldsHandled && <Alert tone="error">{errorMessage(mutation.error)}</Alert>}
        <TextField
          label="Nombre"
          autoComplete="name"
          error={formState.errors.displayName?.message}
          {...register('displayName')}
        />
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
          autoComplete="new-password"
          hint="Mínimo 12 caracteres. Evita contraseñas comunes."
          error={formState.errors.password?.message}
          {...register('password')}
        />
        <fieldset className="flex flex-col gap-2 text-sm">
          <legend className="sr-only">Consentimientos</legend>
          <label className="flex items-start gap-2">
            <input type="checkbox" className="mt-1 size-4 accent-[var(--color-primary)]" {...register('acceptTerms')} />
            <span>
              Acepto los <span className="font-medium">términos de uso</span> (borrador sujeto a revisión legal).
            </span>
          </label>
          {formState.errors.acceptTerms && (
            <p className="text-danger">{formState.errors.acceptTerms.message}</p>
          )}
          <label className="flex items-start gap-2">
            <input type="checkbox" className="mt-1 size-4 accent-[var(--color-primary)]" {...register('acceptPrivacy')} />
            <span>
              He leído el <span className="font-medium">aviso de privacidad</span>.
            </span>
          </label>
          {formState.errors.acceptPrivacy && (
            <p className="text-danger">{formState.errors.acceptPrivacy.message}</p>
          )}
        </fieldset>
        <Button type="submit" loading={mutation.isPending} fullWidth>
          Crear cuenta
        </Button>
        <p className="text-center text-sm text-muted">
          ¿Ya tienes cuenta?{' '}
          <Link to="/login" className="font-semibold text-primary underline-offset-4 hover:underline">
            Inicia sesión
          </Link>
        </p>
      </form>
    </AuthLayout>
  );
}
