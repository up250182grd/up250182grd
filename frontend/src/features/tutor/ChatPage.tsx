import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, FlaskConical, Send, Square } from 'lucide-react';
import { api, ApiError, streamPost, unwrap, type Schemas } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, Skeleton } from '@/components/ui/Card';
import { errorMessage } from '@/features/auth/schemas';
import { LanguageToggle, useLang } from '@/lib/lang';
import { formatMXN } from '@/lib/money';
import { cn } from '@/lib/cn';
import { modeLabels } from './TutorHomePage';

type Conversation = Schemas['ConversationView'];
type Correction = Schemas['CorrectionView'];

interface TurnResult {
  reply: string;
  corrections: Correction[];
  turns: number;
  measured: Schemas['CostEstimate'];
  reservedMxn: number;
}

interface ChatLine {
  role: 'USER' | 'ASSISTANT';
  content: string;
}

const categoryLabels: Record<string, string> = {
  GRAMMAR: 'Gramática',
  VOCABULARY: 'Vocabulario',
  SPELLING: 'Ortografía',
  WORD_ORDER: 'Orden',
  OTHER: 'Otro',
};

function CorrectionItem({ c }: { c: Correction }) {
  const { lang } = useLang();
  return (
    <li className="rounded-lg border border-border p-3 text-sm">
      <p>
        <span className="text-danger line-through decoration-1">“{c.original}”</span>
        <span aria-hidden> → </span>
        <span className="sr-only"> corregido: </span>
        <strong className="text-success">“{c.correction}”</strong>
      </p>
      <p className="mt-1 text-muted">{lang === 'en' ? c.explanationEn || c.explanationEs : c.explanationEs || c.explanationEn}</p>
      <Badge className="mt-2">{categoryLabels[c.category ?? 'OTHER'] ?? c.category}</Badge>
    </li>
  );
}

function SettlementCard({ conversation }: { conversation: Conversation }) {
  const s = conversation.settlement;
  if (!s) return null;
  return (
    <Card role="status" className="flex flex-col gap-2">
      <h2 className="font-semibold">Resumen de la sesión</h2>
      <p className="text-sm text-muted">
        {conversation.turns} mensajes · {conversation.corrections?.length ?? 0} correcciones
        {conversation.closeReason === 'IDLE' && ' · cerrada por inactividad'}
      </p>
      {Object.entries(conversation.correctionsByCategory ?? {}).length > 0 && (
        <p className="text-sm">
          Para repasar:{' '}
          {Object.entries(conversation.correctionsByCategory ?? {})
            .map(([cat, n]) => `${categoryLabels[cat] ?? cat} (${n})`)
            .join(', ')}
        </p>
      )}
      {s.status === 'PENDING_RECONCILIATION' ? (
        <Alert tone="info">El cargo está pendiente de conciliación: {s.note}</Alert>
      ) : (
        <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
          <dt className="text-muted">Costo de IA</dt>
          <dd className="text-right font-mono">{formatMXN(s.directCostMxn)}</dd>
          <dt className="text-muted">{s.internal ? 'Recargo (admin al costo)' : 'Servicio (30 %)'}</dt>
          <dd className="text-right font-mono">{formatMXN(s.markupMxn)}</dd>
          <dt className="text-muted">Impuestos</dt>
          <dd className="text-right font-mono">{formatMXN(s.taxMxn)}</dd>
          <dt className="font-semibold">Total cobrado</dt>
          <dd className="text-right font-mono font-semibold" data-testid="settlement-total">
            {formatMXN(s.totalMxn)}
          </dd>
        </dl>
      )}
    </Card>
  );
}

export function ChatPage() {
  const { conversationId = '' } = useParams();
  const qc = useQueryClient();
  const key = ['tutor', 'conversation', conversationId];
  const conversation = useQuery({
    queryKey: key,
    queryFn: () =>
      unwrap(api.GET('/api/v1/tutor/conversations/{id}', { params: { path: { id: conversationId } } })),
  });

  // Lo confirmado vive en la caché de la consulta; aquí solo el turno en curso (mensaje enviado + respuesta en streaming).
  const [pending, setPending] = useState<ChatLine[]>([]);
  const [draft, setDraft] = useState('');
  const [streaming, setStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const bottom = useRef<HTMLDivElement>(null);

  const confirmed: ChatLine[] = (conversation.data?.messages ?? []).map((m) => ({
    role: m.role as ChatLine['role'],
    content: m.content ?? '',
  }));
  const lines = [...confirmed, ...pending];
  const corrections = conversation.data?.corrections ?? [];
  const measured = conversation.data?.measured;

  useEffect(() => {
    bottom.current?.scrollIntoView?.({ behavior: 'smooth', block: 'end' });
  }, [lines.length, pending]);

  const close = useMutation({
    mutationFn: () =>
      unwrap(api.POST('/api/v1/tutor/conversations/{id}/close', { params: { path: { id: conversationId } } })),
    onSuccess: (c) => {
      qc.setQueryData(key, c);
      qc.invalidateQueries({ queryKey: ['wallet'] });
      qc.invalidateQueries({ queryKey: ['tutor', 'list'] });
    },
  });

  const send = async () => {
    const text = draft.trim();
    if (!text || streaming) return;
    setError(null);
    setDraft('');
    setStreaming(true);
    setPending([
      { role: 'USER', content: text },
      { role: 'ASSISTANT', content: '' },
    ]);
    try {
      await streamPost(`/api/v1/tutor/conversations/${conversationId}/messages`, { text }, (event, data) => {
        if (event === 'delta') {
          const delta = (data as { text: string }).text;
          setPending((prev) => [prev[0]!, { role: 'ASSISTANT', content: (prev[1]?.content ?? '') + delta }]);
        } else if (event === 'done') {
          const result = data as TurnResult;
          const now = new Date().toISOString();
          // El turno queda confirmado: se incorpora a la caché con lo que devolvió el servidor.
          qc.setQueryData<Conversation>(key, (old) =>
            old && {
              ...old,
              turns: result.turns,
              measured: result.measured,
              reservedMxn: result.reservedMxn,
              messages: [
                ...(old.messages ?? []),
                { role: 'USER', content: text, createdAt: now },
                { role: 'ASSISTANT', content: result.reply, createdAt: now },
              ],
              corrections: [...(old.corrections ?? []), ...result.corrections],
            },
          );
          setPending([]);
        } else if (event === 'error') {
          setError((data as { message: string }).message);
          setPending([]);
          qc.invalidateQueries({ queryKey: key });
        }
      });
    } catch (e) {
      setPending([]);
      setDraft(text);
      setError(e instanceof ApiError ? e.message : 'No se pudo enviar el mensaje.');
    } finally {
      setStreaming(false);
      qc.invalidateQueries({ queryKey: ['wallet'] });
    }
  };

  if (conversation.isPending) return <Skeleton className="mx-auto h-96 max-w-5xl" />;
  if (conversation.isError) return <Alert tone="error">{errorMessage(conversation.error)}</Alert>;
  const c = conversation.data;
  const open = c.status === 'OPEN';

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Link to="/app/tutor" className="inline-flex items-center gap-1 text-sm text-muted hover:text-text">
          <ArrowLeft aria-hidden className="size-4" /> Profesor IA
        </Link>
        <LanguageToggle />
      </div>
      <header className="flex flex-wrap items-center gap-2">
        <h1 className="text-xl font-bold tracking-tight">{c.topic ?? 'Conversación'}</h1>
        <Badge tone="primary">{modeLabels[c.mode ?? ''] ?? c.mode}</Badge>
        {c.model?.simulated && (
          <Badge tone="warning">
            <FlaskConical aria-hidden className="size-3.5" /> Profesor simulado
          </Badge>
        )}
        {!open && <Badge>Cerrada</Badge>}
      </header>

      <div className="grid gap-4 lg:grid-cols-[1fr_300px]">
        <Card className="flex min-h-[60dvh] flex-col p-0">
          <div aria-live="polite" aria-busy={streaming} className="flex flex-1 flex-col gap-3 overflow-y-auto p-4">
            {lines.length === 0 && (
              <p className="m-auto max-w-sm text-center text-muted">
                Escribe tu primer mensaje en inglés. ¡No tengas miedo de equivocarte!
              </p>
            )}
            {lines.map((line, i) => (
              <div
                key={i}
                className={cn(
                  'max-w-[85%] whitespace-pre-wrap rounded-2xl px-4 py-2',
                  line.role === 'USER' ? 'self-end bg-primary text-on-primary' : 'self-start bg-surface-2',
                )}
              >
                <span className="sr-only">{line.role === 'USER' ? 'Tú: ' : 'Profesor: '}</span>
                <span lang="en">{line.content || (streaming ? '…' : '')}</span>
              </div>
            ))}
            <div ref={bottom} />
          </div>
          {error && (
            <div className="px-4">
              <Alert tone="error">{error}</Alert>
            </div>
          )}
          {open ? (
            <form
              className="flex items-end gap-2 border-t border-border p-3"
              onSubmit={(e) => {
                e.preventDefault();
                void send();
              }}
            >
              <label htmlFor="chat-input" className="sr-only">
                Tu mensaje en inglés
              </label>
              <textarea
                id="chat-input"
                rows={2}
                maxLength={2000}
                value={draft}
                lang="en"
                placeholder="Write in English…"
                onChange={(e) => setDraft(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    void send();
                  }
                }}
                className="min-h-12 flex-1 resize-none rounded-lg border border-border bg-surface px-3 py-2"
              />
              <Button type="submit" loading={streaming} disabled={!draft.trim()} icon={<Send aria-hidden className="size-4" />}>
                Enviar
              </Button>
            </form>
          ) : null}
        </Card>

        <aside className="flex flex-col gap-4">
          <Card className="flex flex-col gap-1 text-sm">
            <h2 className="font-semibold">Consumo</h2>
            <p className="flex justify-between">
              <span className="text-muted">Medido (sin liquidar)</span>
              <span className="font-mono" data-testid="measured-cost">
                {measured?.available ? formatMXN(measured.totalMxn) : '—'}
              </span>
            </p>
            {c.reservedMxn != null && c.reservedMxn > 0 && (
              <p className="flex justify-between">
                <span className="text-muted">Reservado</span>
                <span className="font-mono">{formatMXN(c.reservedMxn)}</span>
              </p>
            )}
            <p className="text-xs text-muted">
              El consumo lo informa el proveedor al servidor; se cobra al cerrar la conversación.
              {c.model?.simulated && ' Este profesor es simulado: las tarifas son ficticias y no hay costo real.'}
            </p>
            {open && (
              <Button
                variant="secondary"
                className="mt-2"
                icon={<Square aria-hidden className="size-4" />}
                loading={close.isPending}
                disabled={streaming}
                onClick={() => close.mutate()}
              >
                Terminar conversación
              </Button>
            )}
            {close.isError && <Alert tone="error">{errorMessage(close.error)}</Alert>}
          </Card>

          {!open && <SettlementCard conversation={c} />}

          <Card>
            <h2 className="mb-2 font-semibold">Correcciones ({corrections.length})</h2>
            {corrections.length === 0 ? (
              <p className="text-sm text-muted">Aquí aparecerán tus errores corregidos.</p>
            ) : (
              <ul className="flex flex-col gap-2">
                {corrections.map((corr, i) => (
                  <CorrectionItem key={i} c={corr} />
                ))}
              </ul>
            )}
          </Card>
        </aside>
      </div>
    </div>
  );
}
