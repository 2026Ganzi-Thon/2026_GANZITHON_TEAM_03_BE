package com.ganzithon.maeum.global.activity;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
@Profile("auth")
public class ActivityStore {
    private final JdbcTemplate db;

    public ActivityStore(JdbcTemplate db) {
        this.db = db;
    }

    public List<Map<String, Object>> list(String sql, Object... args) {
        return db.query(
                sql,
                (rs, row) -> {
                    Map<String, Object> values =
                            new org.springframework.util.LinkedCaseInsensitiveMap<>();
                    var metadata = rs.getMetaData();
                    for (int i = 1; i <= metadata.getColumnCount(); i++) {
                        Object value =
                                switch (metadata.getColumnType(i)) {
                                    case java.sql.Types.TIMESTAMP ->
                                            rs.getObject(i, java.time.LocalDateTime.class);
                                    case java.sql.Types.DATE ->
                                            rs.getObject(i, java.time.LocalDate.class);
                                    default -> rs.getObject(i);
                                };
                        values.put(metadata.getColumnLabel(i), value);
                    }
                    return values;
                },
                args);
    }

    public Map<String, Object> one(String sql, Object... args) {
        var rows = list(sql, args);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Map<String, Object> required(String sql, String code, Object... args) {
        var row = one(sql, args);
        if (row == null) throw ActivityApi.problem(404, code);
        return row;
    }

    public void lockUser(long user) {
        required("SELECT id FROM users WHERE id=? FOR UPDATE", "USER_NOT_FOUND", user);
    }

    public int update(String sql, Object... args) {
        return db.update(sql, args);
    }

    public long insert(String sql, Object... args) {
        KeyHolder key = new GeneratedKeyHolder();
        db.update(
                c -> {
                    var stmt = c.prepareStatement(sql, new String[] {"id"});
                    for (int i = 0; i < args.length; i++) stmt.setObject(i + 1, args[i]);
                    return stmt;
                },
                key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
}
