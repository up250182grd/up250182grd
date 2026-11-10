package com.englishos.shared.ids;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UlidTest {

    @Test
    void generatesValidUniqueIds() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            String id = Ulid.next();
            assertThat(Ulid.isValid(id)).as(id).isTrue();
            assertThat(seen.add(id)).isTrue();
        }
    }

    @Test
    void encodesTimeSoIdsSortByCreation() {
        String earlier = Ulid.next(1_700_000_000_000L);
        String later = Ulid.next(1_700_000_000_001L);
        assertThat(earlier.substring(0, 10)).isLessThan(later.substring(0, 10));
        assertThat(Ulid.next(0L)).startsWith("0000000000");
    }

    @Test
    void rejectsInvalidValues() {
        assertThat(Ulid.isValid(null)).isFalse();
        assertThat(Ulid.isValid("short")).isFalse();
        assertThat(Ulid.isValid("8ZZZZZZZZZZZZZZZZZZZZZZZZZ")).isFalse();
        assertThat(Ulid.isValid("0123456789ABCDEFGHJKMNPQRI")).isFalse(); // la I no es Crockford
        assertThatThrownBy(() -> Ulid.next(-1L)).isInstanceOf(IllegalArgumentException.class);
    }
}
