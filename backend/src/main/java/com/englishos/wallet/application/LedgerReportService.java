package com.englishos.wallet.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.englishos.shared.text.Csv;
import com.englishos.wallet.domain.LedgerAccount;
import com.englishos.wallet.domain.LedgerEntry;
import com.englishos.wallet.domain.LedgerTransaction;
import com.englishos.wallet.infrastructure.LedgerAccountRepository;
import com.englishos.wallet.infrastructure.LedgerEntryRepository;
import com.englishos.wallet.infrastructure.LedgerTransactionRepository;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exportación del libro mayor: una fila por línea de asiento; cada asiento suma cero. */
@Service
public class LedgerReportService {

    private final LedgerTransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final LedgerAccountRepository accounts;

    LedgerReportService(LedgerTransactionRepository transactions, LedgerEntryRepository entries,
            LedgerAccountRepository accounts) {
        this.transactions = transactions;
        this.entries = entries;
        this.accounts = accounts;
    }

    @PreAuthorize("hasAuthority('billing:read:any')")
    @Transactional(readOnly = true)
    public String ledgerCsv(Instant from, Instant to) {
        List<LedgerTransaction> txs = transactions.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(from, to);
        Map<Long, LedgerTransaction> byId = txs.stream()
                .collect(Collectors.toMap(LedgerTransaction::getId, Function.identity()));
        Map<Long, LedgerAccount> accountsById = accounts.findAll().stream()
                .collect(Collectors.toMap(LedgerAccount::getId, Function.identity()));
        StringBuilder sb = new StringBuilder(Csv.BOM);
        sb.append(Csv.row(List.of("fecha_utc", "asiento", "tipo", "usuario_id", "descripcion", "referencia_tipo",
                "referencia_id", "cuenta", "cuenta_usuario_id", "importe_mxn")));
        List<LedgerEntry> lines = byId.isEmpty() ? List.of() : entries.findByTransactionIdIn(byId.keySet());
        lines.stream()
                .sorted(java.util.Comparator.comparing(LedgerEntry::getTransactionId))
                .forEach(e -> {
                    LedgerTransaction t = byId.get(e.getTransactionId());
                    LedgerAccount a = accountsById.get(e.getAccountId());
                    sb.append(Csv.row(java.util.Arrays.asList(t.getCreatedAt(), t.getPublicId(), t.getType().name(),
                            t.getUserId(), t.getDescription(), t.getRefType(), t.getRefId(), a.getCode().name(),
                            a.getCode().isUserAccount() ? a.getOwnerKey() : null, e.getAmount())));
                });
        return sb.toString();
    }
}
