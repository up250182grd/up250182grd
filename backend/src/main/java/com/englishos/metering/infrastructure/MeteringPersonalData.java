package com.englishos.metering.infrastructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.englishos.shared.privacy.SqlPersonalData;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Datos personales del módulo metering (exportación y cierre de cuenta). */
@Component
class MeteringPersonalData extends SqlPersonalData {

    MeteringPersonalData(JdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    public String section() {
        return "consumo";
    }

    @Override
    protected Map<String, String> exportQueries() {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("liquidaciones", "select public_id, subject_type, status, direct_cost_mxn, factor_applied, markup_mxn, tax_mxn, total_mxn, settled_at from usage_settlements where user_id = ?");
        return q;
    }

    @Override
    protected List<String> eraseStatements() {
        return List.of();
    }
}
