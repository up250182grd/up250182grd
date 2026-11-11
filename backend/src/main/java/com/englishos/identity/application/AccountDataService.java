package com.englishos.identity.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.englishos.audit.AuditService;
import com.englishos.identity.api.CurrentUser;
import com.englishos.identity.domain.RoleCode;
import com.englishos.identity.domain.User;
import com.englishos.identity.infrastructure.UserProfileRepository;
import com.englishos.identity.infrastructure.UserRepository;
import com.englishos.shared.error.ApiException;
import com.englishos.shared.privacy.PersonalDataContributor;
import com.englishos.wallet.api.WalletApi;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derechos del titular (acceso y cancelación). La exportación reúne lo que cada módulo declara. El cierre de cuenta
 * borra el contenido personal (respuestas, mensajes, transcripciones, vocabulario, logros), conserva anonimizados los
 * movimientos contables y cobros que la ley obliga a guardar, y revoca todas las sesiones.
 */
@Service
public class AccountDataService {

    public static final String CONFIRMATION = "ELIMINAR";

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final List<PersonalDataContributor> contributors;
    private final WalletApi wallet;
    private final PasswordEncoder passwords;
    private final SessionRevoker sessions;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    AccountDataService(UserRepository users, UserProfileRepository profiles, List<PersonalDataContributor> contributors,
            WalletApi wallet, PasswordEncoder passwords, SessionRevoker sessions, AuditService audit, JdbcTemplate jdbc,
            Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.contributors = contributors;
        this.wallet = wallet;
        this.passwords = passwords;
        this.sessions = sessions;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Todos los datos del usuario en un documento (derecho de acceso). Sin secretos. */
    @Transactional(readOnly = true)
    public Map<String, Object> export(CurrentUser user) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generado", Instant.now(clock).toString());
        out.put("nota", "Datos personales que EnglishOS guarda sobre ti. Las contraseñas y los tokens no se incluyen; "
                + "el audio de las llamadas nunca se guarda.");
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("cuenta", jdbc.queryForList("""
                select public_id, email, email_verified_at, status, last_login_at, created_at from users where id = ?""",
                user.id()));
        identity.put("perfil", jdbc.queryForList("""
                select display_name, explanation_language, timezone, daily_goal_minutes, store_transcripts,
                       onboarding_completed, created_at, updated_at from user_profiles where user_id = ?""", user.id()));
        identity.put("roles", jdbc.queryForList(
                "select r.code from user_roles ur join roles r on r.id = ur.role_id where ur.user_id = ?", user.id()));
        identity.put("consentimientos", jdbc.queryForList(
                "select document_type, document_version, accepted_at from consents where user_id = ?", user.id()));
        out.put("identidad", identity);
        for (PersonalDataContributor c : contributors) {
            out.put(c.section(), c.export(user.id()));
        }
        return out;
    }

    public record DeletionResult(boolean deleted, BigDecimal forfeitedBalanceMxn) {
    }

    @Transactional
    public DeletionResult delete(CurrentUser actor, String password, String confirmation, boolean acceptBalanceLoss) {
        User user = users.findById(actor.id())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Cuenta no encontrada."));
        if (!CONFIRMATION.equals(confirmation)) {
            throw ApiException.badRequest("CONFIRMATION_REQUIRED", "Escribe " + CONFIRMATION + " para confirmar.");
        }
        if (password == null || !passwords.matches(password, user.getPasswordHash())) {
            throw ApiException.forbidden("INVALID_PASSWORD", "La contraseña no es correcta.");
        }
        if (user.hasRole(RoleCode.ADMIN) && !users.existsOtherActiveAdmin(user.getId())) {
            throw ApiException.conflict("LAST_ADMIN",
                    "Eres el único administrador: asigna otro administrador antes de eliminar tu cuenta.");
        }
        BigDecimal reserved = jdbc.queryForObject("""
                select coalesce(sum(amount_reserved), 0) from wallet_reservations
                where user_id = ? and status = 'ACTIVE'""", BigDecimal.class, user.getId());
        if (reserved != null && reserved.signum() > 0) {
            throw ApiException.conflict("ACTIVE_SESSION",
                    "Tienes una conversación o llamada abierta. Termínala antes de eliminar tu cuenta.");
        }
        BigDecimal available = wallet.available(user.getId());
        if (available.signum() > 0 && !acceptBalanceLoss) {
            throw new ApiException(HttpStatus.CONFLICT, "BALANCE_NOT_ZERO", "Tienes " + available.toPlainString()
                    + " MXN de saldo. Solicita su devolución antes, o confirma que renuncias a él.");
        }
        Instant now = Instant.now(clock);
        for (PersonalDataContributor c : contributors) {
            c.erase(user.getId());
        }
        jdbc.update("delete from auth_tokens where user_id = ?", user.getId());
        String email = user.getEmail();
        profiles.findById(user.getId()).ifPresent(p -> p.anonymize(now));
        user.anonymize(passwords.encode(UUID.randomUUID().toString() + UUID.randomUUID()), now);
        audit.record(user.getId(), "ACCOUNT_DELETED", "USER", user.getPublicId(), null,
                Map.of("forfeitedBalanceMxn", available.toPlainString()));
        sessions.revokeAll(email);
        return new DeletionResult(true, available);
    }
}
