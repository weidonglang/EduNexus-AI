package weidonglang.tianshiwebside.ai;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import weidonglang.tianshiwebside.common.trace.TraceIdHolder;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AiCallLogService {
    private final JdbcTemplate jdbcTemplate;

    public AiCallLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(Principal principal, String functionType, String promptSummary, String modelName,
                       long durationMs, boolean success, String errorMessage) {
        record(principal, functionType, promptSummary, modelName, modelName, durationMs, success, errorMessage, null, null);
    }

    public void record(Principal principal, String functionType, String promptSummary, String modelName,
                       String serviceMode, long durationMs, boolean success, String errorMessage,
                       Long sessionId, Long modelId) {
        String username = principal == null ? "anonymous" : principal.getName();
        Long userId = findUserId(username);
        String roles = roles(principal);
        String cleanServiceMode = serviceMode == null || serviceMode.isBlank() ? modelName : serviceMode;
        jdbcTemplate.update("""
                        insert into ai_call_log
                          (user_id, username, role_codes, function_type, prompt_summary, model_name,
                           service_mode, duration_ms, success, level, error_message, trace_id, session_id, model_id, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                userId,
                username,
                truncate(roles, 240),
                functionType,
                truncate(promptSummary, 500),
                truncate(modelName, 120),
                truncate(cleanServiceMode, 120),
                durationMs,
                success,
                level(success, cleanServiceMode, errorMessage),
                truncate(errorMessage, 500),
                TraceIdHolder.get(),
                sessionId,
                modelId,
                Instant.now()
        );
    }

    public List<AiCallLogRow> recentLogs(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.query("""
                        select id, username, role_codes, function_type, prompt_summary, model_name,
                               service_mode, duration_ms, success, level, error_message, trace_id, session_id, model_id, created_at
                        from ai_call_log
                        order by created_at desc
                        limit ?
                        """,
                (rs, rowNum) -> mapRow(rs),
                safeLimit
        );
    }

    public List<AiCallLogRow> logs(AiCallLogQuery query, int size, int offset) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int safeOffset = Math.max(0, offset);
        SqlAndArgs filtered = filterSql(query);
        List<Object> args = new ArrayList<>(filtered.args());
        args.add(safeSize);
        args.add(safeOffset);
        return jdbcTemplate.query("""
                        select id, username, role_codes, function_type, prompt_summary, model_name,
                               service_mode, duration_ms, success, level, error_message, trace_id,
                               session_id, model_id, created_at
                        from ai_call_log
                        %s
                        order by created_at desc
                        limit ? offset ?
                        """.formatted(filtered.whereSql()),
                (rs, rowNum) -> mapRow(rs),
                args.toArray()
        );
    }

    public long countLogs(AiCallLogQuery query) {
        SqlAndArgs filtered = filterSql(query);
        Long total = jdbcTemplate.queryForObject(
                "select count(*) from ai_call_log " + filtered.whereSql(),
                Long.class,
                filtered.args().toArray()
        );
        return total == null ? 0 : total;
    }

    public record AiCallLogQuery(
            String keyword,
            String username,
            String functionType,
            Boolean success,
            String level,
            Instant startAt,
            Instant endAt
    ) {
    }

    private SqlAndArgs filterSql(AiCallLogQuery query) {
        if (query == null) {
            return new SqlAndArgs("", List.of());
        }
        List<String> clauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (hasText(query.keyword())) {
            clauses.add("(username like ? or function_type like ? or prompt_summary like ? or model_name like ? or service_mode like ? or error_message like ? or trace_id like ?)");
            String keyword = "%" + query.keyword().trim() + "%";
            for (int i = 0; i < 7; i++) {
                args.add(keyword);
            }
        }
        if (hasText(query.username())) {
            clauses.add("username like ?");
            args.add("%" + query.username().trim() + "%");
        }
        if (hasText(query.functionType())) {
            clauses.add("function_type = ?");
            args.add(query.functionType().trim().toUpperCase(Locale.ROOT));
        }
        if (query.success() != null) {
            clauses.add("success = ?");
            args.add(query.success());
        }
        if (hasText(query.level())) {
            clauses.add("level = ?");
            args.add(query.level().trim().toUpperCase(Locale.ROOT));
        }
        if (query.startAt() != null) {
            clauses.add("created_at >= ?");
            args.add(query.startAt());
        }
        if (query.endAt() != null) {
            clauses.add("created_at <= ?");
            args.add(query.endAt());
        }
        return new SqlAndArgs(clauses.isEmpty() ? "" : "where " + String.join(" and ", clauses), args);
    }

    private Long findUserId(String username) {
        if (username == null || username.equals("anonymous")) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject("select id from sys_user where username = ?", Long.class, username);
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private String roles(Principal principal) {
        if (principal instanceof Authentication authentication) {
            return String.join(",", authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .sorted()
                    .toList());
        }
        return "";
    }

    private AiCallLogRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        long sessionId = rs.getLong("session_id");
        Long nullableSessionId = rs.wasNull() ? null : sessionId;
        long modelId = rs.getLong("model_id");
        Long nullableModelId = rs.wasNull() ? null : modelId;
        String serviceMode = rs.getString("service_mode");
        String level = rs.getString("level");
        boolean success = rs.getBoolean("success");
        String errorMessage = rs.getString("error_message");
        return new AiCallLogRow(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("role_codes"),
                rs.getString("function_type"),
                rs.getString("prompt_summary"),
                rs.getString("model_name"),
                serviceMode,
                rs.getLong("duration_ms"),
                success,
                hasText(level) ? level : level(success, serviceMode, errorMessage),
                errorMessage,
                rs.getString("trace_id"),
                nullableSessionId,
                nullableModelId,
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private String level(boolean success, String serviceMode, String errorMessage) {
        if (!success) {
            return "ERROR";
        }
        String normalizedMode = serviceMode == null ? "" : serviceMode.toLowerCase(Locale.ROOT);
        if (normalizedMode.contains("fallback") || normalizedMode.contains("degraded") || normalizedMode.contains("local")) {
            return "WARN";
        }
        if (hasText(errorMessage)) {
            return "WARN";
        }
        return "INFO";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record SqlAndArgs(String whereSql, List<Object> args) {
    }
}
