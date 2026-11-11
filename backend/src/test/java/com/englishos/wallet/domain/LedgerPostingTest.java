package com.englishos.wallet.domain;

import java.math.BigDecimal;

import com.englishos.wallet.domain.LedgerPosting.AccountRef;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerPostingTest {

    private static final AccountRef AVAILABLE = AccountRef.user(AccountCode.USER_AVAILABLE, 7);
    private static final AccountRef RESERVED = AccountRef.user(AccountCode.USER_RESERVED, 7);

    @Test
    void balancedPostingIsAccepted() {
        var p = new LedgerPosting()
                .add(RESERVED, new BigDecimal("-1.30"))
                .add(AccountRef.business(AccountCode.REVENUE_AI_COST_RECOVERY), new BigDecimal("1.00"))
                .add(AccountRef.business(AccountCode.REVENUE_MARKUP), new BigDecimal("0.30"));
        assertThat(p.lines()).hasSize(3);
    }

    @Test
    void unbalancedPostingIsRejected() {
        var p = new LedgerPosting().add(AVAILABLE, new BigDecimal("1.00")).add(RESERVED, new BigDecimal("-0.99"));
        assertThatThrownBy(p::lines).hasMessageContaining("desbalanceado");
    }

    @Test
    void singleLineAndZeroAmountsAreRejectedOrIgnored() {
        var p = new LedgerPosting().add(AVAILABLE, BigDecimal.ZERO).add(RESERVED, new BigDecimal("1.00"));
        assertThatThrownBy(p::lines).hasMessageContaining("dos líneas");
    }

    @Test
    void amountsMustHaveAtMostTwoDecimals() {
        assertThatThrownBy(() -> new LedgerPosting().add(AVAILABLE, new BigDecimal("0.001")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new LedgerPosting().move(AVAILABLE, RESERVED, new BigDecimal("2.5000")).lines())
                .allSatisfy(l -> assertThat(l.amount().scale()).isEqualTo(2));
    }

    @Test
    void accountOwnershipIsChecked() {
        assertThatThrownBy(() -> AccountRef.user(AccountCode.REVENUE_MARKUP, 7)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccountRef.business(AccountCode.USER_AVAILABLE)).isInstanceOf(IllegalArgumentException.class);
    }
}
