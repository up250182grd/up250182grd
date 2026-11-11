/**
 * Conexión de audio WebRTC del navegador con el proveedor de voz. El navegador SOLO genera la oferta SDP; el
 * servidor la envía al proveedor con su propia clave y devuelve la respuesta SDP. La clave nunca llega aquí.
 *
 * 🔍 Flujo según la guía WebRTC de OpenAI (oferta → respuesta SDP, canal de datos "oai-events"); no se ha probado
 * contra el servicio real desde este proyecto.
 */
export interface VoiceConnection {
  pc: RTCPeerConnection;
  mic: MediaStream;
  remoteAudio: HTMLAudioElement;
  offerSdp: string;
  /** Aplica la respuesta SDP devuelta por el servidor. */
  accept(answerSdp: string): Promise<void>;
  setMuted(muted: boolean): void;
  close(): void;
}

export class MicrophoneError extends Error {
  constructor(
    message: string,
    readonly reason: 'denied' | 'missing' | 'unsupported' | 'other',
  ) {
    super(message);
  }
}

export function webRtcSupported(): boolean {
  return typeof window !== 'undefined' && 'RTCPeerConnection' in window && !!navigator.mediaDevices?.getUserMedia;
}

/** Pide el micrófono. Debe llamarse dentro del gesto del usuario (clic) para que iOS/Safari lo permitan. */
export async function requestMicrophone(): Promise<MediaStream> {
  if (!webRtcSupported()) {
    throw new MicrophoneError('Tu navegador no permite llamadas de voz. Prueba con Chrome, Edge, Firefox o Safari recientes.', 'unsupported');
  }
  try {
    return await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
    });
  } catch (e) {
    const name = e instanceof DOMException ? e.name : '';
    if (name === 'NotAllowedError' || name === 'SecurityError') {
      throw new MicrophoneError(
        'No diste permiso para usar el micrófono. Actívalo en la configuración del sitio de tu navegador y vuelve a intentar.',
        'denied',
      );
    }
    if (name === 'NotFoundError' || name === 'OverconstrainedError') {
      throw new MicrophoneError('No encontramos un micrófono. Conecta uno y vuelve a intentar.', 'missing');
    }
    throw new MicrophoneError('No se pudo acceder al micrófono.', 'other');
  }
}

export async function createConnection(mic: MediaStream): Promise<VoiceConnection> {
  const pc = new RTCPeerConnection();
  const remoteAudio = new Audio();
  remoteAudio.autoplay = true;
  remoteAudio.setAttribute('playsinline', '');
  pc.ontrack = (e) => {
    remoteAudio.srcObject = e.streams[0] ?? null;
    void remoteAudio.play().catch(() => undefined);
  };
  mic.getAudioTracks().forEach((t) => pc.addTrack(t, mic));
  pc.createDataChannel('oai-events');
  const offer = await pc.createOffer();
  await pc.setLocalDescription(offer);
  return {
    pc,
    mic,
    remoteAudio,
    offerSdp: offer.sdp ?? '',
    async accept(answerSdp: string) {
      await pc.setRemoteDescription({ type: 'answer', sdp: answerSdp });
    },
    setMuted(muted: boolean) {
      mic.getAudioTracks().forEach((t) => (t.enabled = !muted));
    },
    close() {
      mic.getTracks().forEach((t) => t.stop());
      pc.getSenders().forEach((s) => s.track?.stop());
      pc.close();
      remoteAudio.srcObject = null;
    },
  };
}
