import { afterEach, describe, expect, it, vi } from 'vitest';
import { endReasonLabel, formatDuration, servicePercent } from './VoiceSummary';
import { MicrophoneError, requestMicrophone } from './webrtc';

describe('formatDuration', () => {
  it('formatea minutos y segundos', () => {
    expect(formatDuration(0)).toBe('0:00');
    expect(formatDuration(65)).toBe('1:05');
    expect(formatDuration(900)).toBe('15:00');
    expect(formatDuration(-3)).toBe('0:00');
  });
});

describe('endReasonLabel', () => {
  it('explica cada motivo de fin en español', () => {
    expect(endReasonLabel('RESERVE_LIMIT')).toContain('saldo reservado');
    expect(endReasonLabel('CLIENT_LOST')).toContain('conexión');
    expect(endReasonLabel('PROVIDER_closed')).toBe('El proveedor de voz cerró la llamada.');
    expect(endReasonLabel(null)).toBe('');
  });
});

describe('servicePercent', () => {
  it('deriva el porcentaje del factor aplicado, sin suponer 30 %', () => {
    expect(servicePercent(1.3)).toBe('30 %');
    expect(servicePercent(1)).toBe('0 %');
    expect(servicePercent(1.5)).toBe('50 %');
  });
});

describe('requestMicrophone', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('distingue permiso denegado de micrófono inexistente', async () => {
    vi.stubGlobal('RTCPeerConnection', class {});
    const getUserMedia = vi.fn();
    vi.stubGlobal('navigator', { mediaDevices: { getUserMedia } });

    getUserMedia.mockRejectedValueOnce(new DOMException('no', 'NotAllowedError'));
    await expect(requestMicrophone()).rejects.toMatchObject({ reason: 'denied' });

    getUserMedia.mockRejectedValueOnce(new DOMException('no', 'NotFoundError'));
    await expect(requestMicrophone()).rejects.toMatchObject({ reason: 'missing' });
  });

  it('avisa si el navegador no soporta WebRTC', async () => {
    vi.stubGlobal('navigator', {});
    await expect(requestMicrophone()).rejects.toBeInstanceOf(MicrophoneError);
  });
});
