package com.englishos.identity.application;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * Revoca todas las sesiones de servidor de un usuario (suspensión, cambio de rol, cambio de contraseña).
 * El nombre del principal en Spring Session es el correo normalizado del usuario.
 */
@Component
public class SessionRevoker {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    SessionRevoker(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    public int revokeAll(String principalName) {
        var found = sessions.findByPrincipalName(principalName);
        found.keySet().forEach(sessions::deleteById);
        return found.size();
    }
}
