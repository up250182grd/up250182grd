package com.englishos.payments.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Red de seguridad: si una notificación se perdió, la conciliación periódica consulta la pasarela. */
@Component
class PaymentReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJob.class);

    private final PaymentService payments;

    PaymentReconciliationJob(PaymentService payments) {
        this.payments = payments;
    }

    @Scheduled(fixedDelayString = "${app.payments.reconcile-every:PT15M}",
            initialDelayString = "${app.payments.reconcile-every:PT15M}")
    void run() {
        try {
            int n = payments.reconcileOpen();
            if (n > 0) {
                log.info("Conciliación de pagos: {} pagos revisados", n);
            }
        } catch (RuntimeException e) {
            log.error("Falló la conciliación de pagos", e);
        }
    }
}
