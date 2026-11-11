package com.englishos.wallet.domain;

/**
 * Cuentas del libro mayor (RN-19). Convención: cada asiento suma cero; las cuentas del usuario tienen saldo positivo
 * cuando hay fondos; las de ingresos y gastos del negocio acumulan en positivo; las de contrapartida (recargas
 * recibidas, costo de proveedor por pagar) acumulan en negativo.
 */
public enum AccountCode {
    USER_AVAILABLE(true),
    USER_RESERVED(true),
    /** Contrapartida de recargas simuladas (modo prueba). Las recargas reales usarán la cuenta de la pasarela. */
    SIMULATED_TOPUP_CLEARING(false),
    /** Contrapartida de recargas cobradas por la pasarela (dinero a recibir del proveedor de pagos). */
    GATEWAY_CLEARING(false),
    REVENUE_AI_COST_RECOVERY(false),
    REVENUE_MARKUP(false),
    TAX_PAYABLE(false),
    INTERNAL_AI_EXPENSE(false),
    PROVIDER_COST_ACCRUED(false);

    private final boolean userAccount;

    AccountCode(boolean userAccount) {
        this.userAccount = userAccount;
    }

    public boolean isUserAccount() {
        return userAccount;
    }
}
