package com.englishos.ai.infrastructure;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

import com.englishos.ai.api.RealtimeVoiceProvider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Llamada SIMULADA: no hay audio ni red. Cada {@code tick} emite por el "canal lateral" un turno de conversación
 * ficticio (transcripción + uso con tarifas ficticias). Permite probar de punta a punta reservas, medidor en vivo,
 * cortes por límite, desconexiones y liquidación sin costo real. La interfaz lo etiqueta como simulado.
 */
@Component
@ConditionalOnProperty(name = "app.ai.voice-provider", havingValue = "fake", matchIfMissing = true)
class FakeRealtimeVoiceProvider implements RealtimeVoiceProvider {

    private static final List<String[]> SCRIPT = List.of(
            new String[] {"Hello! (simulated call) How are you today?", "I am fine, thank you."},
            new String[] {"Great! What did you do last weekend?", "I visited my grandmother."},
            new String[] {"Nice! What did you talk about?", "We talked about my new job."},
            new String[] {"Interesting! Tell me more about your job.", "I work in a bank."});

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2,
            Thread.ofVirtual().name("fake-voice-", 0).factory());
    private final Map<String, ScheduledFuture<?>> calls = new ConcurrentHashMap<>();
    private final Duration tick;

    FakeRealtimeVoiceProvider(@Value("${app.ai.fake-voice.tick:PT3S}") Duration tick) {
        this.tick = tick;
    }

    @Override
    public String provider() {
        return "FAKE";
    }

    @Override
    public boolean simulated() {
        return true;
    }

    @Override
    public CallStarted startCall(CallRequest request) {
        return new CallStarted("fake-call-" + UUID.randomUUID(), null);
    }

    @Override
    public Sideband openSideband(String callId, Listener listener) {
        AtomicInteger turn = new AtomicInteger();
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            int n = turn.getAndIncrement();
            String[] lines = SCRIPT.get(n % SCRIPT.size());
            listener.onTranscript("ASSISTANT", lines[0]);
            listener.onTranscript("USER", lines[1]);
            Map<String, Long> units = new LinkedHashMap<>();
            units.put("text_in", 100L + 40L * n); // el contexto crece con la conversación
            units.put("audio_in", 30L);
            units.put("audio_out", 45L);
            units.put("text_out", 10L);
            listener.onUsage(callId + "-resp-" + n, units);
        }, tick.toMillis(), tick.toMillis(), TimeUnit.MILLISECONDS);
        calls.put(callId, task);
        return new Sideband() {
            @Override
            public void sendJson(String event) {
                // El simulador ignora eventos (no hay modelo real).
            }

            @Override
            public void close() {
                ScheduledFuture<?> t = calls.remove(callId);
                if (t != null) {
                    t.cancel(false);
                }
            }
        };
    }

    @Override
    public void hangup(String callId) {
        ScheduledFuture<?> t = calls.remove(callId);
        if (t != null) {
            t.cancel(false);
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
