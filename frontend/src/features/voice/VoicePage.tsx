import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FlaskConical, Mic, MicOff, Phone, PhoneOff } from 'lucide-react';
import { api, unwrap, type Schemas } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, Skeleton } from '@/components/ui/Card';
import { TextField } from '@/components/ui/TextField';
import { errorMessage } from '@/features/auth/schemas';
import { isInsufficientFunds } from '@/features/wallet/WalletPage';
import { modeLabels } from '@/features/tutor/TutorHomePage';
import { formatMXN } from '@/lib/money';
import { createConnection, MicrophoneError, requestMicrophone, type VoiceConnection } from './webrtc';
import { endReasonLabel, formatDuration, Transcript, VoiceSettlement } from './VoiceSummary';

type Mode = 'FREE' | 'GUIDED' | 'ROLEPLAY';
type StartResult = Schemas['VoiceStartResult'];

interface Meter {
  elapsedSeconds: number;
  measured?: Schemas['CostEstimate'];
  reservedMxn?: number;
}

interface Line {
  role: string;
  text: string;
}

const modes: { value: Mode; title: string; text: string; placeholder: string }[] = [
  { value: 'FREE', title: 'Conversación libre', text: 'Habla de lo que quieras; el profesor te mantiene conversando.', placeholder: 'Ej.: mi fin de semana' },
  { value: 'GUIDED', title: 'Práctica guiada', text: 'Practica en voz alta un tema gramatical concreto.', placeholder: 'Ej.: pasado simple' },
  { value: 'ROLEPLAY', title: 'Roleplay', text: 'Simula una situación real con el profesor en personaje.', placeholder: 'Ej.: entrevista de trabajo' },
];

const HEARTBEAT_MS = 10_000;
const dateTime = new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' });

export const voiceKeys = {
  quote: ['voice', 'quote'] as const,
  list: ['voice', 'list'] as const,
  session: (id: string) => ['voice', 'session', id] as const,
};

/** Nivel del micrófono (0–1) para el indicador visual; se detiene al desmontar. */
function useMicLevel(stream: MediaStream | null): number {
  const [level, setLevel] = useState(0);
  useEffect(() => {
    if (!stream) return;
    const AudioCtx = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioCtx) return;
    const ctx = new AudioCtx();
    const analyser = ctx.createAnalyser();
    analyser.fftSize = 512;
    ctx.createMediaStreamSource(stream).connect(analyser);
    const data = new Uint8Array(analyser.fftSize);
    let frame = 0;
    const loop = () => {
      analyser.getByteTimeDomainData(data);
      let peak = 0;
      for (const v of data) peak = Math.max(peak, Math.abs(v - 128));
      setLevel(Math.min(1, peak / 64));
      frame = requestAnimationFrame(loop);
    };
    frame = requestAnimationFrame(loop);
    return () => {
      cancelAnimationFrame(frame);
      void ctx.close();
    };
  }, [stream]);
  return stream ? level : 0;
}

function LevelBars({ level, label }: { level: number; label: string }) {
  const bars = [0.15, 0.35, 0.55, 0.75, 0.95];
  return (
    <div className="flex h-10 items-end gap-1" role="img" aria-label={label}>
      {bars.map((t) => (
        <span
          key={t}
          className={`w-2 rounded-sm transition-all ${level >= t ? 'bg-primary' : 'bg-border'}`}
          style={{ height: `${20 + t * 80}%` }}
        />
      ))}
    </div>
  );
}

function CallPanel({ call, connection, onFinished }: { call: StartResult; connection: VoiceConnection | null; onFinished: () => void }) {
  const queryClient = useQueryClient();
  const id = call.sessionId ?? '';
  const [startedAt] = useState(() => Date.now());
  const [now, setNow] = useState(startedAt);
  const [meter, setMeter] = useState<Meter>({ elapsedSeconds: 0, reservedMxn: call.reservedMxn });
  const [lines, setLines] = useState<Line[]>([]);
  const [warning, setWarning] = useState<string | null>(null);
  const [endingReason, setEndingReason] = useState<string | null>(null);
  const [muted, setMuted] = useState(false);
  const level = useMicLevel(connection?.mic ?? null);
  const transcriptEnd = useRef<HTMLDivElement>(null);
  const ending = endingReason !== null;

  const end = useMutation({
    mutationFn: () => unwrap(api.POST('/api/v1/voice/sessions/{id}/end', { params: { path: { id } } })),
  });

  // Reloj local para el contador; el servidor es quien mide el consumo.
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  // Latido: si el navegador se cierra o pierde la red, el servidor termina la llamada sola.
  useEffect(() => {
    if (ending) return;
    const t = setInterval(() => {
      void api.POST('/api/v1/voice/sessions/{id}/heartbeat', { params: { path: { id } } });
    }, HEARTBEAT_MS);
    return () => clearInterval(t);
  }, [id, ending]);

  // Eventos en vivo del servidor: medidor, transcripción, avisos y fin.
  useEffect(() => {
    const source = new EventSource(`/api/v1/voice/sessions/${id}/events`, { withCredentials: true });
    const parse = (e: MessageEvent) => JSON.parse(e.data as string) as Record<string, unknown>;
    source.addEventListener('meter', (e) => setMeter(parse(e) as unknown as Meter));
    source.addEventListener('transcript', (e) => {
      const d = parse(e);
      setLines((prev) => [...prev, { role: String(d.role), text: String(d.text) }]);
    });
    source.addEventListener('warning', (e) => setWarning(String(parse(e).message)));
    source.addEventListener('ending', (e) => setEndingReason(String(parse(e).reason)));
    source.addEventListener('ended', () => {
      source.close();
      setEndingReason((r) => r ?? 'USER');
      void queryClient.invalidateQueries({ queryKey: ['wallet'] });
      onFinished();
    });
    return () => source.close();
  }, [id, queryClient, onFinished]);

  useEffect(() => {
    if (ending) connection?.close();
  }, [ending, connection]);

  useEffect(() => {
    transcriptEnd.current?.scrollIntoView?.({ block: 'nearest' });
  }, [lines.length]);

  const elapsed = Math.max(meter.elapsedSeconds, Math.floor((now - startedAt) / 1000));
  const max = call.maxDurationSeconds ?? 0;
  const measured = meter.measured?.totalMxn;

  function hangUp() {
    setEndingReason('USER');
    end.mutate();
  }

  return (
    <div className="flex flex-col gap-4">
      {call.simulated && (
        <Alert tone="info">
          <span className="inline-flex items-center gap-1 font-semibold">
            <FlaskConical aria-hidden className="size-4" /> Llamada simulada (sin audio).
          </span>{' '}
          No hay proveedor de voz configurado: el diálogo y el consumo son ficticios, pero la reserva, el medidor y la
          liquidación funcionan igual que en una llamada real.
        </Alert>
      )}
      <Card className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center gap-4">
          <div className="flex-1">
            <p className="text-sm text-muted">{ending ? 'Terminando llamada…' : 'En llamada con el profesor'}</p>
            <p className="font-mono text-3xl font-bold" aria-live="off" data-testid="voice-timer">
              {formatDuration(elapsed)}
              <span className="text-base font-normal text-muted"> / {formatDuration(max)}</span>
            </p>
          </div>
          {!call.simulated && <LevelBars level={muted ? 0 : level} label={muted ? 'Micrófono silenciado' : 'Nivel del micrófono'} />}
          <dl className="grid grid-cols-2 gap-x-4 text-sm">
            <dt className="text-muted">Consumo medido</dt>
            <dd className="text-right font-mono font-semibold" data-testid="voice-measured">
              {measured != null ? formatMXN(measured) : '—'}
            </dd>
            {(meter.reservedMxn ?? 0) > 0 && (
              <>
                <dt className="text-muted">Reservado</dt>
                <dd className="text-right font-mono">{formatMXN(meter.reservedMxn)}</dd>
              </>
            )}
          </dl>
        </div>
        {warning && <Alert tone="error">{warning}</Alert>}
        {endingReason && endingReason !== 'USER' && <Alert tone="info">{endReasonLabel(endingReason)}</Alert>}
        <div className="flex flex-wrap gap-2">
          {!call.simulated && (
            <Button
              variant="secondary"
              disabled={ending}
              aria-pressed={muted}
              onClick={() => {
                connection?.setMuted(!muted);
                setMuted(!muted);
              }}
              icon={muted ? <MicOff aria-hidden className="size-4" /> : <Mic aria-hidden className="size-4" />}
            >
              {muted ? 'Activar micrófono' : 'Silenciar'}
            </Button>
          )}
          <Button variant="danger" loading={ending} disabled={ending} onClick={hangUp} icon={<PhoneOff aria-hidden className="size-4" />}>
            Colgar
          </Button>
        </div>
        {end.isError && <Alert tone="error">{errorMessage(end.error)}</Alert>}
      </Card>
      <Card>
        <h2 className="mb-3 font-semibold">Transcripción en vivo</h2>
        <div className="flex max-h-80 flex-col overflow-y-auto" aria-live="polite">
          <Transcript lines={lines} />
          <div ref={transcriptEnd} />
        </div>
      </Card>
    </div>
  );
}

function SessionResult({ id, onAgain }: { id: string; onAgain: () => void }) {
  const session = useQuery({
    queryKey: voiceKeys.session(id),
    queryFn: () => unwrap(api.GET('/api/v1/voice/sessions/{id}', { params: { path: { id } } })),
    refetchInterval: (q) => (q.state.data?.settlement ? false : 1000),
  });
  if (!session.data?.settlement) return <Skeleton className="h-40" />;
  return (
    <div className="flex flex-col gap-4">
      <VoiceSettlement session={session.data} />
      <Card>
        <h2 className="mb-3 font-semibold">Transcripción</h2>
        <Transcript lines={session.data.transcript ?? []} stored={session.data.transcriptStored} />
      </Card>
      <div>
        <Button onClick={onAgain} icon={<Phone aria-hidden className="size-4" />}>
          Hacer otra llamada
        </Button>
      </div>
    </div>
  );
}

export function VoicePage() {
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<Mode>('FREE');
  const [topic, setTopic] = useState('');
  const [call, setCall] = useState<StartResult | null>(null);
  const [finishedId, setFinishedId] = useState<string | null>(null);
  const [micError, setMicError] = useState<string | null>(null);
  const connection = useRef<VoiceConnection | null>(null);
  const [conn, setConn] = useState<VoiceConnection | null>(null);

  const quote = useQuery({ queryKey: voiceKeys.quote, queryFn: () => unwrap(api.GET('/api/v1/voice/quote')) });
  const history = useQuery({ queryKey: voiceKeys.list, queryFn: () => unwrap(api.GET('/api/v1/voice/sessions')) });

  const start = useMutation({
    mutationFn: async () => {
      setMicError(null);
      const simulated = quote.data?.model?.simulated ?? true;
      let c: VoiceConnection | null = null;
      if (!simulated) {
        // Dentro del gesto del usuario: permiso del micrófono y oferta WebRTC.
        const mic = await requestMicrophone();
        c = await createConnection(mic);
      }
      try {
        const result = await unwrap(
          api.POST('/api/v1/voice/sessions', { body: { mode, topic: topic || undefined, sdpOffer: c?.offerSdp } }),
        );
        if (c && result.sdpAnswer) await c.accept(result.sdpAnswer);
        return { result, c };
      } catch (e) {
        c?.close();
        throw e;
      }
    },
    onSuccess: ({ result, c }) => {
      connection.current = c;
      setConn(c);
      setFinishedId(null);
      setCall(result);
      void queryClient.invalidateQueries({ queryKey: ['wallet'] });
    },
    onError: (e) => {
      if (e instanceof MicrophoneError) setMicError(e.message);
    },
  });

  // Si el usuario sale de la página con la llamada abierta, se cuelga (el latido es el respaldo).
  useEffect(
    () => () => {
      connection.current?.close();
    },
    [],
  );

  const onFinished = () => {
    if (!call?.sessionId) return;
    connection.current = null;
    setFinishedId(call.sessionId);
    setCall(null);
    setConn(null);
    void queryClient.invalidateQueries({ queryKey: ['voice'] });
  };

  const q = quote.data;
  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Conversación por voz</h1>
        <p className="mt-1 text-muted">Habla en inglés con el profesor como en una llamada.</p>
      </div>

      {call ? (
        <CallPanel call={call} connection={conn} onFinished={onFinished} />
      ) : finishedId ? (
        <SessionResult id={finishedId} onAgain={() => setFinishedId(null)} />
      ) : (
        <Card className="flex flex-col gap-4">
          {quote.isPending && <Skeleton className="h-24" />}
          {q && (
            <div className="flex flex-col gap-2 rounded-lg bg-surface-2 p-3 text-sm" data-testid="voice-quote">
              <p className="flex flex-wrap items-center gap-2">
                <span className="font-semibold">{q.model?.displayName}</span>
                {q.model?.simulated && <Badge tone="warning">Simulado (sin audio)</Badge>}
              </p>
              {q.admin ? (
                <p>Como administrador, tu consumo se registra al costo (sin recargo) como gasto interno.</p>
              ) : (
                <p>
                  Costo estimado: <strong>{formatMXN(q.estimatedPerMinuteMxn)} por minuto</strong> (estimación; se cobra
                  lo que el proveedor mida). Saldo disponible {formatMXN(q.availableMxn)}
                  {q.monthlyLimitMxn != null && ` · gastado este mes ${formatMXN(q.spentThisMonthMxn)} de ${formatMXN(q.monthlyLimitMxn)}`}.
                </p>
              )}
              {q.canStart && <p>Duración máxima de esta llamada: {q.maxMinutes} minutos.</p>}
              {!q.canStart && q.reason && <p className="font-semibold text-danger">{q.reason}</p>}
            </div>
          )}
          <fieldset>
            <legend className="mb-2 font-semibold">¿Cómo quieres practicar?</legend>
            <div className="grid gap-3 sm:grid-cols-3">
              {modes.map((m) => (
                <label
                  key={m.value}
                  className={`flex cursor-pointer flex-col gap-1 rounded-lg border p-3 ${
                    mode === m.value ? 'border-primary bg-primary/10' : 'border-border hover:bg-surface-2'
                  }`}
                >
                  <span className="flex items-center gap-2 font-medium">
                    <input
                      type="radio"
                      name="voice-mode"
                      value={m.value}
                      checked={mode === m.value}
                      onChange={() => setMode(m.value)}
                      className="accent-[var(--color-primary)]"
                    />
                    {m.title}
                  </span>
                  <span className="text-sm text-muted">{m.text}</span>
                </label>
              ))}
            </div>
          </fieldset>
          <TextField
            label="Tema o situación (opcional)"
            value={topic}
            maxLength={200}
            onChange={(e) => setTopic(e.target.value)}
            placeholder={modes.find((m) => m.value === mode)?.placeholder}
          />
          {!q?.model?.simulated && (
            <p className="text-sm text-muted">
              Te pediremos permiso para usar el micrófono. El audio va directo al proveedor de voz y no se guarda.
            </p>
          )}
          {micError && <Alert tone="error">{micError}</Alert>}
          {start.isError && !micError && (
            <Alert tone="error">
              {errorMessage(start.error)}
              {isInsufficientFunds(start.error) && (
                <Link to="/app/wallet" className="ml-1 font-semibold text-primary underline-offset-4 hover:underline">
                  Ir a la billetera
                </Link>
              )}
            </Alert>
          )}
          <div>
            <Button
              loading={start.isPending}
              disabled={!q?.canStart}
              onClick={() => start.mutate()}
              icon={<Phone aria-hidden className="size-4" />}
            >
              Iniciar llamada
            </Button>
          </div>
        </Card>
      )}

      {!call && (
        <section aria-labelledby="voice-recent">
          <h2 id="voice-recent" className="mb-3 text-lg font-semibold">
            Llamadas recientes
          </h2>
          {history.data?.length === 0 && <p className="text-muted">Todavía no tienes llamadas.</p>}
          <ul className="flex flex-col gap-2">
            {history.data?.map((s) => (
              <li key={s.id}>
                <Link
                  to={`/app/voice/${s.id}`}
                  className="flex min-h-14 flex-wrap items-center gap-2 rounded-lg border border-border bg-surface px-4 py-2 hover:bg-surface-2"
                >
                  <Badge tone="primary">{modeLabels[s.mode ?? ''] ?? s.mode}</Badge>
                  <span className="flex-1 font-medium">{s.topic ?? 'Sin tema'}</span>
                  <span className="font-mono text-sm text-muted">{formatDuration(s.connectedSeconds ?? 0)}</span>
                  <span className="w-full text-xs text-muted sm:w-auto">
                    {s.createdAt ? dateTime.format(new Date(s.createdAt)) : ''}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
