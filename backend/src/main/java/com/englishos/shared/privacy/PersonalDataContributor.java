package com.englishos.shared.privacy;

import java.util.List;
import java.util.Map;

/**
 * Cada módulo declara los datos personales que guarda de un usuario: para entregárselos (derecho de acceso) y para
 * eliminarlos al cerrar la cuenta (derecho de cancelación). Así identidad no necesita conocer las tablas de los
 * demás módulos.
 *
 * <p>Los registros que la ley obliga a conservar (movimientos y cobros) se exportan pero NO se borran: quedan
 * asociados a una cuenta anonimizada.
 */
public interface PersonalDataContributor {

    /** Nombre de la sección en la exportación (p. ej. "aprendizaje"). */
    String section();

    /** Datos del usuario como tablas → filas. Nunca incluye secretos (contraseñas, tokens). */
    Map<String, List<Map<String, Object>>> export(Long userId);

    /** Borra (o despersonaliza) lo que no se debe conservar. Se ejecuta en la transacción del cierre de cuenta. */
    void erase(Long userId);
}
