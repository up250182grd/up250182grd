package com.englishos.wallet.infrastructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.englishos.shared.privacy.SqlPersonalData;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Datos personales del módulo wallet (exportación y cierre de cuenta). */
@Component
class WalletPersonalData extends SqlPersonalData {

    WalletPersonalData(JdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    public String section() {
        return "billetera";
    }

    @Override
    protected Map<String, String> exportQueries() {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("movimientos", "select t.public_id, t.type, t.description, t.created_at, a.code as cuenta, e.amount from ledger_transactions t join ledger_entries e on e.transaction_id = t.id join ledger_accounts a on a.id = e.account_id where t.user_id = ? and a.owner_key = t.user_id");
        q.put("preferencias", "select monthly_limit_mxn, low_balance_threshold_mxn, updated_at from wallet_preferences where user_id = ?");
        return q;
    }

    @Override
    protected List<String> eraseStatements() {
        return List.of(
                "delete from wallet_preferences where user_id = ?");
    }
}
