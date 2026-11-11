import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Download, Trash2 } from 'lucide-react';
import { api, ApiError, unwrap } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { TextField } from '@/components/ui/TextField';
import { errorMessage } from '@/features/auth/schemas';

/** Derechos del titular: descargar mis datos y eliminar mi cuenta. */
export function PrivacyCard() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [acceptBalanceLoss, setAcceptBalanceLoss] = useState(false);
  const remove = useMutation({
    mutationFn: () =>
      unwrap(
        api.POST('/api/v1/me/account/delete', {
          body: { password, confirmation, acceptBalanceLoss },
        }),
      ),
    onSuccess: () => {
      queryClient.clear();
      navigate('/', { replace: true });
    },
  });
  const needsBalanceConsent = remove.error instanceof ApiError && remove.error.code === 'BALANCE_NOT_ZERO';

  const submit = (e: FormEvent) => {
    e.preventDefault();
    remove.mutate();
  };

  return (
    <Card className="flex flex-col gap-4">
      <div>
        <h2 className="font-semibold">Privacidad</h2>
        <p className="text-sm text-muted">
          Consulta qué datos guardamos en el{' '}
          <a href="/legal/privacidad" target="_blank" rel="noopener" className="text-primary underline underline-offset-4">
            aviso de privacidad
          </a>
          . El audio de las llamadas nunca se guarda.
        </p>
      </div>
      <div>
        {/* Descarga directa con la sesión: el navegador guarda el JSON (el servidor indica el nombre del archivo). */}
        <a
          href="/api/v1/me/data-export"
          download="mis-datos-englishos.json"
          className="inline-flex min-h-11 items-center gap-2 rounded-lg border border-border px-4 text-sm font-semibold hover:bg-surface-2"
        >
          <Download aria-hidden className="size-4" /> Descargar mis datos
        </a>
      </div>
      <div className="border-t border-border pt-4">
        {!open ? (
          <Button variant="secondary" icon={<Trash2 aria-hidden className="size-4" />} onClick={() => setOpen(true)}>
            Eliminar mi cuenta…
          </Button>
        ) : (
          <form onSubmit={submit} className="flex flex-col gap-3" aria-labelledby="delete-title">
            <h3 id="delete-title" className="font-semibold text-danger-ink">
              Eliminar mi cuenta
            </h3>
            <p className="text-sm">
              Se borran tus respuestas, conversaciones, transcripciones, vocabulario y logros, y se cierra tu sesión en
              todos tus dispositivos. Los movimientos de tu billetera y los cobros se conservan sin tus datos, porque la
              ley obliga a guardarlos. <strong>No se puede deshacer.</strong>
            </p>
            <TextField
              label="Tu contraseña"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            <TextField
              label='Escribe "ELIMINAR" para confirmar'
              value={confirmation}
              onChange={(e) => setConfirmation(e.target.value)}
              autoComplete="off"
            />
            {needsBalanceConsent && (
              <label className="flex items-start gap-2 text-sm">
                <input
                  type="checkbox"
                  className="mt-1 size-4 accent-[var(--color-danger)]"
                  checked={acceptBalanceLoss}
                  onChange={(e) => setAcceptBalanceLoss(e.target.checked)}
                />
                <span>Entiendo que renuncio a mi saldo disponible (puedo pedir antes su devolución a soporte).</span>
              </label>
            )}
            {remove.isError && <Alert tone="error">{errorMessage(remove.error)}</Alert>}
            <div className="flex flex-wrap gap-2">
              <Button
                type="submit"
                variant="danger"
                loading={remove.isPending}
                disabled={!password || confirmation !== 'ELIMINAR' || (needsBalanceConsent && !acceptBalanceLoss)}
              >
                Eliminar definitivamente
              </Button>
              <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
                Cancelar
              </Button>
            </div>
          </form>
        )}
      </div>
    </Card>
  );
}
