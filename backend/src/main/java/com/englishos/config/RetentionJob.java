package com.englishos.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.englishos.shared.privacy.RetentionPolicy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Retención de transcripciones (RNF-11): cada día borra el contenido conversacional más antiguo que el plazo. */
@Component
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final List<RetentionPolicy> policies;
    private final Clock clock;
    private final Duration retention;

    RetentionJob(List<RetentionPolicy> policies, Clock clock,
            @Value("${app.privacy.transcript-retention:180d}") Duration retention) {
        this.policies = policies;
        this.clock = clock;
        this.retention = retention;
    }

    @Scheduled(cron = "${app.privacy.retention-cron:0 30 3 * * *}")
    void scheduled() {
        run();
    }

    /** Devuelve el total de registros borrados. */
    @Transactional
    public int run() {
        Instant before = Instant.now(clock).minus(retention);
        int total = 0;
        for (RetentionPolicy p : policies) {
            int n = p.purgeBefore(before);
            total += n;
            if (n > 0) {
                log.info("Retención: {} registros de {} anteriores a {}", n, p.name(), before);
            }
        }
        return total;
    }
}
