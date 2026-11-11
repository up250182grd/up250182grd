import { Link, useParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, PhoneOff } from 'lucide-react';
import { api, unwrap } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, Skeleton } from '@/components/ui/Card';
import { errorMessage } from '@/features/auth/schemas';
import { modeLabels } from '@/features/tutor/TutorHomePage';
import { voiceKeys } from './VoicePage';
import { Transcript, VoiceSettlement } from './VoiceSummary';

/** Detalle de una llamada pasada (o una que quedó abierta en otra pestaña). */
export function VoiceSessionPage() {
  const { sessionId = '' } = useParams();
  const queryClient = useQueryClient();
  const session = useQuery({
    queryKey: voiceKeys.session(sessionId),
    queryFn: () => unwrap(api.GET('/api/v1/voice/sessions/{id}', { params: { path: { id: sessionId } } })),
    refetchInterval: (q) => (q.state.data && !q.state.data.settlement && q.state.data.status !== 'FAILED' ? 2000 : false),
  });
  const end = useMutation({
    mutationFn: () => unwrap(api.POST('/api/v1/voice/sessions/{id}/end', { params: { path: { id: sessionId } } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['voice'] }),
  });

  const s = session.data;
  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">
      <Link to="/app/voice" className="inline-flex w-fit items-center gap-1 text-sm text-primary hover:underline">
        <ArrowLeft aria-hidden className="size-4" /> Volver a voz
      </Link>
      {session.isPending && <Skeleton className="h-40" />}
      {session.isError && <Alert tone="error">{errorMessage(session.error)}</Alert>}
      {s && (
        <>
          <div>
            <h1 className="text-2xl font-bold tracking-tight">{s.topic ?? 'Llamada sin tema'}</h1>
            <p className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted">
              <Badge tone="primary">{modeLabels[s.mode ?? ''] ?? s.mode}</Badge>
              {s.model?.displayName}
              {s.model?.simulated && <Badge tone="warning">Simulada</Badge>}
            </p>
          </div>
          {s.status === 'ACTIVE' && (
            <Alert tone="info">
              Esta llamada sigue abierta (quizá en otra pestaña). Si ya no la usas, termínala para liberar tu saldo
              reservado.
              <div className="mt-2">
                <Button variant="danger" loading={end.isPending} onClick={() => end.mutate()} icon={<PhoneOff aria-hidden className="size-4" />}>
                  Terminar llamada
                </Button>
              </div>
            </Alert>
          )}
          {s.status === 'FAILED' && <Alert tone="error">La llamada no pudo conectarse. No se cobró nada.</Alert>}
          {s.settlement ? <VoiceSettlement session={s} /> : s.status !== 'FAILED' && s.status !== 'ACTIVE' && <Skeleton className="h-32" />}
          <Card>
            <h2 className="mb-3 font-semibold">Transcripción</h2>
            <Transcript lines={s.transcript ?? []} stored={s.transcriptStored} />
          </Card>
        </>
      )}
    </div>
  );
}
