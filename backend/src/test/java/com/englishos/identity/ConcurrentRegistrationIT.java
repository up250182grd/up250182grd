package com.englishos.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.englishos.support.ApiClient;
import com.englishos.support.IntegrationTest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresión: registros simultáneos provocaban un deadlock de InnoDB (gap locks al invalidar tokens pendientes
 * bajo REPEATABLE READ) y el usuario recibía un error 500. Detectado por las pruebas E2E.
 */
class ConcurrentRegistrationIT extends IntegrationTest {

    @Test
    void parallelRegistrationsAndResendsAllSucceed() throws Exception {
        int n = 12;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<int[]>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            results.add(pool.submit(() -> {
                ApiClient client = newClient();
                String email = uniqueEmail();
                client.get("/api/v1/auth/csrf");
                start.await();
                int registered = register(client, email, STRONG_PASSWORD).status();
                int resent = client.post("/api/v1/auth/resend-verification",
                        java.util.Map.of("email", email)).status();
                return new int[] {registered, resent};
            }));
        }
        start.countDown();
        for (Future<int[]> f : results) {
            int[] statuses = f.get();
            assertThat(statuses[0]).as("registro").isEqualTo(201);
            assertThat(statuses[1]).as("reenvío").isEqualTo(202);
        }
        pool.shutdown();
    }
}
