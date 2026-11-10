package com.englishos.identity.api;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * Principal autenticado guardado en la sesión de servidor. Es inmutable: un cambio de rol o una suspensión
 * invalida las sesiones del usuario, por lo que los permisos aquí guardados nunca quedan desactualizados.
 *
 * <p>Los demás módulos usan esta clase (vía {@code @AuthenticationPrincipal}) para conocer al usuario actual.
 */
public record CurrentUser(Long id, String publicId, String email, Set<String> roles, Set<String> permissions)
        implements AuthenticatedPrincipal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public CurrentUser {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    /** Nombre del principal: lo usa Spring Session para indexar (y revocar) las sesiones del usuario. */
    @Override
    public String getName() {
        return email;
    }

    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }
}
