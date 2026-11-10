import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { z } from 'zod';
import { api, unwrap } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { TextField } from '@/components/ui/TextField';
import { applyFieldErrors, errorMessage } from '@/features/auth/schemas';
import { meQueryKey, useMe, type Me } from '@/features/auth/useSession';

const schema = z.object({
  displayName: z.string().trim().min(1, 'Escribe tu nombre.').max(80),
  explanationLanguage: z.enum(['es', 'en']),
  dailyGoalMinutes: z.coerce.number().int().min(5, 'Mínimo 5 minutos.').max(240, 'Máximo 240 minutos.'),
  storeTranscripts: z.boolean(),
});
type FormValues = z.infer<typeof schema>;

function ProfileForm({ me }: { me: Me }) {
  const qc = useQueryClient();
  const p = me.profile;
  const { register, handleSubmit, formState, setError } = useForm<z.input<typeof schema>, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      displayName: p?.displayName ?? '',
      explanationLanguage: (p?.explanationLanguage as 'es' | 'en' | undefined) ?? 'es',
      dailyGoalMinutes: p?.dailyGoalMinutes ?? 20,
      storeTranscripts: p?.storeTranscripts ?? true,
    },
  });
  const save = useMutation({
    mutationFn: (body: FormValues) => unwrap(api.PATCH('/api/v1/me/profile', { body })),
    onSuccess: () => qc.invalidateQueries({ queryKey: meQueryKey }),
    onError: (e) => applyFieldErrors(e, setError),
  });

  return (
    <form noValidate onSubmit={handleSubmit((v) => save.mutate(v))} className="flex flex-col gap-4">
      {save.isSuccess && <Alert tone="success">Cambios guardados.</Alert>}
      {save.isError && <Alert tone="error">{errorMessage(save.error)}</Alert>}
      <TextField label="Nombre" error={formState.errors.displayName?.message} {...register('displayName')} />
      <div className="flex flex-col gap-1.5">
        <label htmlFor="explanationLanguage" className="text-sm font-medium">
          Idioma de las explicaciones
        </label>
        <select
          id="explanationLanguage"
          className="min-h-11 rounded-lg border border-border bg-surface px-3"
          {...register('explanationLanguage')}
        >
          <option value="es">Español</option>
          <option value="en">English</option>
        </select>
      </div>
      <TextField
        label="Meta diaria (minutos)"
        type="number"
        inputMode="numeric"
        min={5}
        max={240}
        error={formState.errors.dailyGoalMinutes?.message}
        {...register('dailyGoalMinutes')}
      />
      <label className="flex items-start gap-2 text-sm">
        <input type="checkbox" className="mt-1 size-4 accent-[var(--color-primary)]" {...register('storeTranscripts')} />
        <span>
          Guardar las transcripciones de mis conversaciones para dar seguimiento a mi aprendizaje.
          <span className="block text-muted">El audio nunca se almacena.</span>
        </span>
      </label>
      <div>
        <Button type="submit" loading={save.isPending}>
          Guardar cambios
        </Button>
      </div>
    </form>
  );
}

export function SettingsPage() {
  const { data: me } = useMe();
  if (!me) return null;
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <h1 className="text-2xl font-bold tracking-tight">Ajustes</h1>
      <Card>
        <h2 className="mb-1 font-semibold">Perfil</h2>
        <p className="mb-4 text-sm text-muted">{me.email}</p>
        <ProfileForm me={me} />
      </Card>
    </div>
  );
}
