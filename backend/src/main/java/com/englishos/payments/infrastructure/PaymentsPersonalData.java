package com.englishos.payments.infrastructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.englishos.shared.privacy.SqlPersonalData;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Datos personales del módulo payments (exportación y cierre de cuenta). */
@Component
class PaymentsPersonalData extends SqlPersonalData {

    PaymentsPersonalData(JdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    public String section() {
        return "recargas";
    }

    @Override
    protected Map<String, String> exportQueries() {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("recargas", "select public_id, provider, amount_mxn, status, provider_status, refunded_mxn, created_at from payment_intents where user_id = ?");
        return q;
    }

    @Override
    protected List<String> eraseStatements() {
        return List.of();
    }
}
