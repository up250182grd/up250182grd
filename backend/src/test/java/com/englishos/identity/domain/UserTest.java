package com.englishos.identity.domain;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    private static final Instant T0 = Instant.parse("2026-11-10T12:00:00Z");
    private static final Duration LOCK = Duration.ofMinutes(15);

    private static User newUser() {
        return User.register("ana@example.com", "hash", null, T0);
    }

    @Test
    void locksAfterMaxFailuresAndUnlocksWhenTimePasses() {
        User user = newUser();
        for (int i = 0; i < 4; i++) {
            user.recordFailedLogin(5, LOCK, T0);
            assertThat(user.isLocked(T0)).isFalse();
        }
        user.recordFailedLogin(5, LOCK, T0);
        assertThat(user.isLocked(T0)).isTrue();
        assertThat(user.isLocked(T0.plus(LOCK).minusSeconds(1))).isTrue();
        assertThat(user.isLocked(T0.plus(LOCK))).isFalse();
    }

    @Test
    void successfulLoginResetsFailures() {
        User user = newUser();
        for (int i = 0; i < 4; i++) {
            user.recordFailedLogin(5, LOCK, T0);
        }
        user.recordSuccessfulLogin(T0);
        user.recordFailedLogin(5, LOCK, T0);
        assertThat(user.isLocked(T0)).isFalse();
    }

    @Test
    void suspendedUsersCannotLogIn() {
        User user = newUser();
        assertThat(user.canLogIn()).isTrue();
        user.suspend(T0);
        assertThat(user.canLogIn()).isFalse();
        user.reactivate(T0);
        assertThat(user.canLogIn()).isTrue();
    }

    @Test
    void emailVerificationKeepsTheFirstTimestamp() {
        User user = newUser();
        user.markEmailVerified(T0);
        user.markEmailVerified(T0.plusSeconds(60));
        assertThat(user.getEmailVerifiedAt()).isEqualTo(T0);
    }

    @Test
    void publicIdIsAValidUlid() {
        assertThat(newUser().getPublicId()).hasSize(26);
    }
}
