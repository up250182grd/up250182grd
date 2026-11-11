package com.englishos.wallet.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.englishos.shared.error.ApiException;
import com.englishos.wallet.domain.LedgerAccount;
import com.englishos.wallet.domain.LedgerEntry;
import com.englishos.wallet.domain.LedgerPosting;
import com.englishos.wallet.domain.LedgerPosting.AccountRef;
import com.englishos.wallet.domain.LedgerTransaction;
import com.englishos.wallet.infrastructure.LedgerAccountRepository;
import com.englishos.wallet.infrastructure.LedgerEntryRepository;
import com.englishos.wallet.infrastructure.LedgerTransactionRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Único punto que escribe en el libro mayor (ADR-0003). Bloquea las cuentas afectadas en orden de id, comprueba
 * los saldos mínimos, actualiza la caché de saldo e inserta cabecera + líneas en la misma transacción.
 * La clave de idempotencia hace que repetir un evento (reintento, webhook duplicado) no cree un segundo asiento.
 */
@Service
class LedgerService {

    private final LedgerAccountRepository accounts;
    private final LedgerTransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final Clock clock;

    LedgerService(LedgerAccountRepository accounts, LedgerTransactionRepository transactions,
            LedgerEntryRepository entries, Clock clock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.entries = entries;
        this.clock = clock;
    }

    record PostCommand(LedgerTransaction.Type type, Long userId, String idempotencyKey, String description,
            String refType, String refId, Long createdBy, LedgerPosting posting,
            /** Saldo mínimo permitido tras el asiento, por cuenta; las cuentas no listadas no tienen mínimo. */
            Map<AccountRef, BigDecimal> floors) {
    }

    record Posted(LedgerTransaction transaction, boolean alreadyExisted, Map<AccountRef, BigDecimal> balancesAfter) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    Posted post(PostCommand cmd) {
        Optional<LedgerTransaction> existing = transactions.findByIdempotencyKey(cmd.idempotencyKey());
        if (existing.isPresent()) {
            return new Posted(existing.get(), true, Map.of());
        }
        List<LedgerPosting.Line> lines = cmd.posting().lines(); // valida invariantes antes de tocar la BD
        Instant now = Instant.now(clock);

        Map<AccountRef, Long> ids = new HashMap<>();
        for (LedgerPosting.Line line : lines) {
            ids.computeIfAbsent(line.account(), ref -> resolve(ref, now));
        }
        Map<Long, LedgerAccount> locked = new HashMap<>();
        accounts.lockAll(ids.values()).forEach(a -> locked.put(a.getId(), a));

        // Otra transacción pudo registrar la misma clave mientras esperábamos el bloqueo.
        existing = transactions.findByIdempotencyKey(cmd.idempotencyKey());
        if (existing.isPresent()) {
            return new Posted(existing.get(), true, Map.of());
        }

        Map<AccountRef, BigDecimal> after = new HashMap<>();
        for (LedgerPosting.Line line : lines) {
            LedgerAccount account = locked.get(ids.get(line.account()));
            account.apply(line.amount());
            after.put(line.account(), account.getBalance());
        }
        cmd.floors().forEach((ref, floor) -> {
            BigDecimal balance = after.get(ref);
            if (balance != null && balance.compareTo(floor) < 0) {
                throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "INSUFFICIENT_FUNDS",
                        "Saldo insuficiente para esta operación. Recarga saldo para continuar.");
            }
        });

        LedgerTransaction tx = transactions.save(LedgerTransaction.create(cmd.type(), cmd.userId(),
                cmd.idempotencyKey(), cmd.description(), cmd.refType(), cmd.refId(), cmd.createdBy(), now));
        for (LedgerPosting.Line line : lines) {
            entries.save(new LedgerEntry(tx.getId(), ids.get(line.account()), line.amount(), now));
        }
        return new Posted(tx, false, after);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    BigDecimal balance(AccountRef ref) {
        return accounts.findByCodeAndOwnerKey(ref.code(), ref.ownerKey())
                .map(LedgerAccount::getBalance)
                .orElse(BigDecimal.ZERO.setScale(2));
    }

    private Long resolve(AccountRef ref, Instant now) {
        accounts.insertIfAbsent(ref.code().name(), ref.ownerKey(), now);
        return accounts.findIdByCodeAndOwnerKey(ref.code(), ref.ownerKey()).orElseThrow();
    }
}
