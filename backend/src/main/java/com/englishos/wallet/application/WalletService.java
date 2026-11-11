package com.englishos.wallet.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.englishos.audit.AuditService;
import com.englishos.shared.error.ApiException;
import com.englishos.wallet.api.WalletApi;
import com.englishos.wallet.domain.AccountCode;
import com.englishos.wallet.domain.BillingIncident;
import com.englishos.wallet.domain.LedgerAccount;
import com.englishos.wallet.domain.LedgerEntry;
import com.englishos.wallet.domain.LedgerPosting;
import com.englishos.wallet.domain.LedgerPosting.AccountRef;
import com.englishos.wallet.domain.LedgerTransaction;
import com.englishos.wallet.domain.WalletPreferences;
import com.englishos.wallet.domain.WalletReservation;
import com.englishos.wallet.infrastructure.BillingIncidentRepository;
import com.englishos.wallet.infrastructure.LedgerAccountRepository;
import com.englishos.wallet.infrastructure.LedgerEntryRepository;
import com.englishos.wallet.infrastructure.LedgerTransactionRepository;
import com.englishos.wallet.infrastructure.WalletPreferencesRepository;
import com.englishos.wallet.infrastructure.WalletReservationRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Billetera en MXN sobre el libro mayor (RN-18..RN-29). */
@Service
public class WalletService implements WalletApi {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final LedgerService ledger;
    private final LedgerAccountRepository accounts;
    private final LedgerTransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final WalletReservationRepository reservations;
    private final WalletPreferencesRepository preferences;
    private final BillingIncidentRepository incidents;
    private final AuditService audit;
    private final Clock clock;
    private final BigDecimal overdraftLimit;
    private final boolean simulatedTopupsEnabled;
    private final BigDecimal minTopup;
    private final BigDecimal maxTopup;

    WalletService(LedgerService ledger, LedgerAccountRepository accounts, LedgerTransactionRepository transactions,
            LedgerEntryRepository entries, WalletReservationRepository reservations,
            WalletPreferencesRepository preferences, BillingIncidentRepository incidents, AuditService audit,
            Clock clock,
            @Value("${app.billing.overdraft-limit-mxn:10.00}") BigDecimal overdraftLimit,
            @Value("${app.billing.simulated-topups-enabled:false}") boolean simulatedTopupsEnabled,
            @Value("${app.billing.min-topup-mxn:100.00}") BigDecimal minTopup,
            @Value("${app.billing.max-topup-mxn:2000.00}") BigDecimal maxTopup) {
        this.ledger = ledger;
        this.accounts = accounts;
        this.transactions = transactions;
        this.entries = entries;
        this.reservations = reservations;
        this.preferences = preferences;
        this.incidents = incidents;
        this.audit = audit;
        this.clock = clock;
        this.overdraftLimit = overdraftLimit;
        this.simulatedTopupsEnabled = simulatedTopupsEnabled;
        this.minTopup = minTopup;
        this.maxTopup = maxTopup;
    }

    private static AccountRef availableAcc(long userId) {
        return AccountRef.user(AccountCode.USER_AVAILABLE, userId);
    }

    private static AccountRef reservedAcc(long userId) {
        return AccountRef.user(AccountCode.USER_RESERVED, userId);
    }

    private static AccountRef business(AccountCode code) {
        return AccountRef.business(code);
    }

    // ---- API para otros módulos ---------------------------------------------------------------

    @Override
    @Transactional
    public BigDecimal ensureReserved(Long userId, String subjectType, Long subjectId, BigDecimal targetTotal) {
        Instant now = Instant.now(clock);
        WalletReservation r = reservations.findForUpdate(subjectType, subjectId)
                .orElseGet(() -> reservations.saveAndFlush(WalletReservation.open(userId, subjectType, subjectId, now)));
        if (!r.getUserId().equals(userId) || r.getStatus() != WalletReservation.Status.ACTIVE) {
            throw ApiException.conflict("RESERVATION_CLOSED", "La sesión ya fue liquidada.");
        }
        BigDecimal missing = targetTotal.setScale(2, java.math.RoundingMode.UP).subtract(r.getAmountReserved());
        if (missing.signum() <= 0) {
            return r.getAmountReserved();
        }
        // La reserva está bloqueada: cada ampliación parte de un monto distinto, así que la clave es única por paso.
        ledger.post(new LedgerService.PostCommand(LedgerTransaction.Type.RESERVE, userId,
                "reserve:" + subjectType + ":" + subjectId + ":from:" + r.getAmountReserved().toPlainString(),
                "Reserva de saldo para " + label(subjectType), subjectType, String.valueOf(subjectId), userId,
                new LedgerPosting().move(availableAcc(userId), reservedAcc(userId), missing),
                Map.of(availableAcc(userId), BigDecimal.ZERO)));
        r.increase(missing, now);
        return r.getAmountReserved();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal reservedFor(String subjectType, Long subjectId) {
        return reservations.findBySubjectTypeAndSubjectId(subjectType, subjectId)
                .filter(r -> r.getStatus() == WalletReservation.Status.ACTIVE)
                .map(WalletReservation::getAmountReserved)
                .orElse(BigDecimal.ZERO.setScale(2));
    }

    @Override
    @Transactional
    public SettleResult settleCustomer(Long userId, String subjectType, Long subjectId, ChargeLines charge) {
        Instant now = Instant.now(clock);
        WalletReservation r = reservations.findForUpdate(subjectType, subjectId).orElse(null);
        BigDecimal reservedAmount = r == null || r.getStatus() != WalletReservation.Status.ACTIVE
                ? BigDecimal.ZERO.setScale(2) : r.getAmountReserved();
        BigDecimal fromReserved = charge.total().min(reservedAmount);
        BigDecimal fromAvailable = charge.total().subtract(fromReserved);
        BigDecimal released = reservedAmount.subtract(fromReserved);
        String key = subjectType + ":" + subjectId;

        Long captureTx = null;
        boolean incident = false;
        if (charge.total().signum() > 0) {
            LedgerService.Posted posted = ledger.post(new LedgerService.PostCommand(LedgerTransaction.Type.CAPTURE,
                    userId, "capture:" + key, "Cargo por " + label(subjectType), subjectType,
                    String.valueOf(subjectId), userId,
                    new LedgerPosting()
                            .add(reservedAcc(userId), fromReserved.negate())
                            .add(availableAcc(userId), fromAvailable.negate())
                            .add(business(AccountCode.REVENUE_AI_COST_RECOVERY), charge.directCost())
                            .add(business(AccountCode.REVENUE_MARKUP), charge.markup())
                            .add(business(AccountCode.TAX_PAYABLE), charge.tax()),
                    Map.of())); // el consumo real siempre se registra; el sobregiro se controla abajo
            captureTx = posted.transaction().getId();
            BigDecimal after = posted.balancesAfter().get(availableAcc(userId));
            if (!posted.alreadyExisted() && after != null && after.compareTo(overdraftLimit.negate()) < 0) {
                incident = true;
                openIncident("OVERDRAFT_ABOVE_LIMIT", userId, subjectType, String.valueOf(subjectId),
                        "El cargo dejó el saldo en " + after + " MXN, por debajo del sobregiro técnico permitido (-"
                                + overdraftLimit + ").");
            }
        }
        if (released.signum() > 0) {
            ledger.post(new LedgerService.PostCommand(LedgerTransaction.Type.RELEASE, userId, "release:" + key,
                    "Liberación de saldo no usado", subjectType, String.valueOf(subjectId), userId,
                    new LedgerPosting().move(reservedAcc(userId), availableAcc(userId), released),
                    Map.of(reservedAcc(userId), BigDecimal.ZERO)));
        }
        if (r != null && r.getStatus() == WalletReservation.Status.ACTIVE) {
            r.settle(fromReserved, released, now);
        }
        return new SettleResult(captureTx, charge.total(), released, incident);
    }

    @Override
    @Transactional
    public Long recordInternalUsage(Long userId, String subjectType, Long subjectId, BigDecimal directCost) {
        if (directCost.signum() == 0) {
            return null;
        }
        return ledger.post(new LedgerService.PostCommand(LedgerTransaction.Type.INTERNAL_USAGE, userId,
                "internal:" + subjectType + ":" + subjectId, "Consumo interno del administrador (al costo)",
                subjectType, String.valueOf(subjectId), userId,
                new LedgerPosting().move(business(AccountCode.PROVIDER_COST_ACCRUED),
                        business(AccountCode.INTERNAL_AI_EXPENSE), directCost),
                Map.of())).transaction().getId();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal available(Long userId) {
        return accounts.findByCodeAndOwnerKey(AccountCode.USER_AVAILABLE, userId)
                .map(LedgerAccount::getBalance).orElse(BigDecimal.ZERO.setScale(2));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal capturedThisMonth(Long userId, ZoneId zone) {
        Instant since = LocalDate.now(clock.withZone(zone)).withDayOfMonth(1).atStartOfDay(zone).toInstant();
        return transactions.capturedSince(userId, since).setScale(2);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> monthlyLimit(Long userId) {
        return preferences.findById(userId).map(WalletPreferences::getMonthlyLimitMxn);
    }

    @Override
    @Transactional
    public void openIncident(String type, Long userId, String refType, String refId, String description) {
        incidents.save(BillingIncident.open(type, userId, refType, refId, description, Instant.now(clock)));
    }

    // ---- Casos de uso del usuario -------------------------------------------------------------

    public record WalletSummary(BigDecimal available, BigDecimal reserved, BigDecimal spentThisMonth,
            BigDecimal monthlyLimit, BigDecimal lowBalanceThreshold, boolean lowBalance, boolean simulatedTopupsEnabled,
            BigDecimal minTopup, BigDecimal maxTopup) {
    }

    public record Movement(String id, String type, String description, BigDecimal availableChange,
            BigDecimal reservedChange, Instant createdAt) {
    }

    @Transactional(readOnly = true)
    public WalletSummary summary(Long userId, ZoneId zone) {
        Map<AccountCode, BigDecimal> balances = new HashMap<>();
        accounts.findByOwnerKeyAndCodeIn(userId, List.of(AccountCode.USER_AVAILABLE, AccountCode.USER_RESERVED))
                .forEach(a -> balances.put(a.getCode(), a.getBalance()));
        BigDecimal avail = balances.getOrDefault(AccountCode.USER_AVAILABLE, BigDecimal.ZERO.setScale(2));
        WalletPreferences prefs = preferences.findById(userId)
                .orElse(WalletPreferences.defaults(userId, Instant.now(clock)));
        return new WalletSummary(avail, balances.getOrDefault(AccountCode.USER_RESERVED, BigDecimal.ZERO.setScale(2)),
                capturedThisMonth(userId, zone), prefs.getMonthlyLimitMxn(), prefs.getLowBalanceThresholdMxn(),
                avail.compareTo(prefs.getLowBalanceThresholdMxn()) < 0, simulatedTopupsEnabled, minTopup, maxTopup);
    }

    @Transactional(readOnly = true)
    public List<Movement> movements(Long userId, int page, int size) {
        List<LedgerTransaction> txs = transactions.findByUser(userId, PageRequest.of(page, size)).getContent();
        if (txs.isEmpty()) {
            return List.of();
        }
        Map<Long, AccountCode> userAccounts = new HashMap<>();
        accounts.findByOwnerKey(userId).forEach(a -> userAccounts.put(a.getId(), a.getCode()));
        Map<Long, BigDecimal[]> changes = new HashMap<>();
        for (LedgerEntry e : entries.findByTransactionIdIn(txs.stream().map(LedgerTransaction::getId).toList())) {
            AccountCode code = userAccounts.get(e.getAccountId());
            if (code == null) {
                continue;
            }
            BigDecimal[] c = changes.computeIfAbsent(e.getTransactionId(),
                    k -> new BigDecimal[] {BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2)});
            c[code == AccountCode.USER_AVAILABLE ? 0 : 1] = c[code == AccountCode.USER_AVAILABLE ? 0 : 1].add(e.getAmount());
        }
        List<Movement> result = new ArrayList<>();
        for (LedgerTransaction t : txs) {
            BigDecimal[] c = changes.getOrDefault(t.getId(),
                    new BigDecimal[] {BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2)});
            result.add(new Movement(t.getPublicId(), t.getType().name(), t.getDescription(), c[0], c[1],
                    t.getCreatedAt()));
        }
        return result;
    }

    /**
     * Recarga SIMULADA (modo prueba): acredita saldo sin cobro real. Solo existe si
     * {@code app.billing.simulated-topups-enabled=true}; las recargas reales llegan con la pasarela (Fase 7).
     */
    @Transactional
    public WalletSummary simulatedTopup(Long userId, BigDecimal amount, String idempotencyKey, ZoneId zone) {
        if (!simulatedTopupsEnabled) {
            throw ApiException.forbidden("SIMULATED_TOPUPS_DISABLED",
                    "Las recargas simuladas están desactivadas en este entorno.");
        }
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "Falta una clave de idempotencia válida.");
        }
        if (amount == null || amount.scale() > 2 || amount.compareTo(minTopup) < 0 || amount.compareTo(maxTopup) > 0) {
            throw ApiException.badRequest("INVALID_TOPUP_AMOUNT",
                    "La recarga debe ser de " + minTopup + " a " + maxTopup + " MXN, con hasta 2 decimales.");
        }
        LedgerService.Posted posted = ledger.post(new LedgerService.PostCommand(LedgerTransaction.Type.TOPUP, userId,
                "topup:sim:" + userId + ":" + idempotencyKey, "Recarga simulada (modo prueba, sin cobro real)",
                "SIMULATED_TOPUP", idempotencyKey, userId,
                new LedgerPosting().move(business(AccountCode.SIMULATED_TOPUP_CLEARING), availableAcc(userId), amount),
                Map.of()));
        if (!posted.alreadyExisted()) {
            audit.record(userId, "WALLET_SIMULATED_TOPUP", "WALLET", String.valueOf(userId), null,
                    Map.of("amount", amount.toPlainString(), "transaction", posted.transaction().getPublicId()));
        }
        return summary(userId, zone);
    }

    @Transactional
    public WalletSummary updatePreferences(Long userId, BigDecimal monthlyLimit, BigDecimal lowBalanceThreshold,
            ZoneId zone) {
        if ((monthlyLimit != null && monthlyLimit.signum() < 0) || lowBalanceThreshold == null
                || lowBalanceThreshold.signum() < 0) {
            throw ApiException.badRequest("INVALID_LIMITS", "Los límites no pueden ser negativos.");
        }
        Instant now = Instant.now(clock);
        WalletPreferences prefs = preferences.findById(userId).orElseGet(() -> WalletPreferences.defaults(userId, now));
        prefs.update(monthlyLimit == null ? null : monthlyLimit.setScale(2, java.math.RoundingMode.HALF_UP),
                lowBalanceThreshold.setScale(2, java.math.RoundingMode.HALF_UP), now);
        preferences.save(prefs);
        return summary(userId, zone);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> businessBalances() {
        Map<String, BigDecimal> out = new java.util.LinkedHashMap<>();
        for (AccountCode code : AccountCode.values()) {
            if (!code.isUserAccount()) {
                out.put(code.name(), accounts.findByCodeAndOwnerKey(code, 0)
                        .map(LedgerAccount::getBalance).orElse(BigDecimal.ZERO.setScale(2)));
            }
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentView> openIncidents() {
        return incidents.findTop100ByStatusOrderByIdDesc("OPEN").stream()
                .map(i -> new IncidentView(i.getId(), i.getType(), i.getUserId(), i.getRefType(), i.getRefId(),
                        i.getDescription(), i.getCreatedAt()))
                .toList();
    }

    // ---- Pasarela de pagos --------------------------------------------------------------------

    @Override
    @Transactional
    public LedgerResult creditGatewayTopup(Long userId, BigDecimal amount, String provider, String providerPaymentId,
            String intentId) {
        LedgerService.Posted posted = ledger.post(new LedgerService.PostCommand(LedgerTransaction.Type.TOPUP, userId,
                "topup:gw:" + provider + ":" + providerPaymentId, "Recarga con " + providerLabel(provider),
                "PAYMENT_INTENT", intentId, null,
                new LedgerPosting().move(business(AccountCode.GATEWAY_CLEARING), availableAcc(userId), amount),
                Map.of()));
        if (!posted.alreadyExisted()) {
            audit.record(null, "WALLET_GATEWAY_TOPUP", "WALLET", String.valueOf(userId), null,
                    Map.of("amount", amount.toPlainString(), "provider", provider, "payment", providerPaymentId,
                            "transaction", posted.transaction().getPublicId()));
        }
        return new LedgerResult(posted.transaction().getPublicId(), posted.alreadyExisted());
    }

    @Override
    @Transactional
    public LedgerResult debitGatewayRefund(Long userId, BigDecimal amount, String provider, String refundId,
            Long actorId) {
        LedgerService.Posted posted = ledger.post(new LedgerService.PostCommand(LedgerTransaction.Type.REFUND, userId,
                "refund:gw:" + provider + ":" + refundId, "Devolución de saldo a tu medio de pago", "PAYMENT_REFUND",
                refundId, actorId,
                new LedgerPosting().move(availableAcc(userId), business(AccountCode.GATEWAY_CLEARING), amount),
                Map.of(availableAcc(userId), BigDecimal.ZERO)));
        return new LedgerResult(posted.transaction().getPublicId(), posted.alreadyExisted());
    }

    @Override
    @Transactional
    public LedgerResult reverseGatewayRefund(Long userId, BigDecimal amount, String provider, String refundId,
            Long actorId) {
        LedgerService.Posted posted = ledger.post(new LedgerService.PostCommand(LedgerTransaction.Type.ADJUSTMENT,
                userId, "refund-reversal:gw:" + provider + ":" + refundId,
                "Reverso de devolución rechazada por la pasarela", "PAYMENT_REFUND", refundId, actorId,
                new LedgerPosting().move(business(AccountCode.GATEWAY_CLEARING), availableAcc(userId), amount),
                Map.of()));
        return new LedgerResult(posted.transaction().getPublicId(), posted.alreadyExisted());
    }

    @Override
    @Transactional
    public boolean resolveIncident(Long incidentId, String note, Long actorId) {
        return incidents.findById(incidentId).map(i -> {
            boolean changed = i.resolve(note, actorId, Instant.now(clock));
            if (changed) {
                audit.record(actorId, "BILLING_INCIDENT_RESOLVED", "BILLING_INCIDENT", String.valueOf(incidentId),
                        Map.of("status", "OPEN"), Map.of("status", "RESOLVED", "note", note));
            }
            return changed;
        }).orElse(false);
    }

    private static String providerLabel(String provider) {
        return "MERCADOPAGO".equals(provider) ? "Mercado Pago" : provider;
    }

    /** Verificación de invariantes del libro (job/admin): Σ global = 0 y caché = Σ líneas por cuenta. */
    @Override
    @Transactional(readOnly = true)
    public List<String> verifyLedger() {
        List<String> problems = new ArrayList<>();
        BigDecimal total = entries.sumAll();
        if (total.signum() != 0) {
            problems.add("La suma global del libro es " + total + " (debe ser 0)");
        }
        for (LedgerAccount a : accounts.findAll()) {
            BigDecimal sum = entries.sumByAccount(a.getId());
            if (sum.compareTo(a.getBalance()) != 0) {
                problems.add("Cuenta " + a.getCode() + "/" + a.getOwnerKey() + ": caché " + a.getBalance()
                        + " ≠ líneas " + sum);
            }
        }
        return problems;
    }

    private static String label(String subjectType) {
        return switch (subjectType) {
            case "TEXT_CONVERSATION" -> "conversación con el profesor IA";
            case "VOICE_SESSION" -> "llamada con el profesor IA";
            default -> "uso de IA";
        };
    }
}
