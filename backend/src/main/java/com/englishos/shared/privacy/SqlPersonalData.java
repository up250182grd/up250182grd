package com.englishos.shared.privacy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

/** Ayuda para contribuyentes basados en SQL: cada consulta recibe el id del usuario como único parámetro. */
public abstract class SqlPersonalData implements PersonalDataContributor {

    protected final JdbcTemplate jdbc;

    protected SqlPersonalData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Tabla → consulta (con un único "?" para el usuario). */
    protected abstract Map<String, String> exportQueries();

    /** Sentencias de borrado en orden (hijos primero), con un único "?" para el usuario. */
    protected abstract List<String> eraseStatements();

    @Override
    public Map<String, List<Map<String, Object>>> export(Long userId) {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        exportQueries().forEach((name, sql) -> out.put(name, jdbc.queryForList(sql, userId)));
        return out;
    }

    @Override
    public void erase(Long userId) {
        eraseStatements().forEach(sql -> jdbc.update(sql, userId));
    }
}
