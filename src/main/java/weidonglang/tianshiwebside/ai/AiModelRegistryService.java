package weidonglang.tianshiwebside.ai;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import weidonglang.tianshiwebside.common.error.BusinessException;
import weidonglang.tianshiwebside.common.error.ErrorCode;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class AiModelRegistryService {
    private final JdbcTemplate jdbcTemplate;

    public AiModelRegistryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AiModelRecord> models() {
        return jdbcTemplate.query("""
                        select id, name, provider, model_name, base_url, api_key_ref, model_type, purpose,
                               enabled, is_default, description, last_status, last_latency_ms, last_error,
                               last_checked_at, created_at, updated_at
                        from ai_model_registry
                        where deleted = false
                        order by model_type, is_default desc, enabled desc, id
                        """,
                (rs, rowNum) -> mapModel(rs));
    }

    public List<AiModelRecord> enabledModels(String modelType) {
        return jdbcTemplate.query("""
                        select id, name, provider, model_name, base_url, api_key_ref, model_type, purpose,
                               enabled, is_default, description, last_status, last_latency_ms, last_error,
                               last_checked_at, created_at, updated_at
                        from ai_model_registry
                        where enabled = true
                          and deleted = false
                          and model_type = ?
                        order by is_default desc, id asc
                        """,
                (rs, rowNum) -> mapModel(rs),
                normalize(modelType));
    }

    public AiModelRecord defaultEnabledModel(String modelType) {
        List<AiModelRecord> rows = enabledModels(modelType);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "没有可用的 " + normalize(modelType) + " 模型");
        }
        return rows.get(0);
    }

    public AiModelRecord requireEnabledChatModel(Long id) {
        AiModelRecord model = id == null ? defaultEnabledModel("CHAT") : require(id);
        if (!model.enabled() || !"CHAT".equals(model.modelType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只能选择已启用的 CHAT 模型");
        }
        return model;
    }

    public AiModelRecord create(AiModelRequest request) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                        insert into ai_model_registry
                          (name, provider, model_name, base_url, api_key_ref, model_type, purpose,
                           enabled, is_default, description, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                clean(request.name()), normalize(request.provider()), clean(request.modelName()), clean(request.baseUrl()),
                clean(request.apiKeyRef()), normalize(request.modelType()), clean(request.purpose()), request.enabled(),
                request.defaultModel(), clean(request.description()), now, now);
        Long id = jdbcTemplate.queryForObject("select max(id) from ai_model_registry", Long.class);
        if (request.defaultModel()) {
            setDefault(id);
        }
        return require(id);
    }

    @Transactional
    public AiModelRecord update(Long id, AiModelRequest request) {
        AiModelRecord existing = require(id);
        jdbcTemplate.update("""
                        update ai_model_registry
                        set name = ?, provider = ?, model_name = ?, base_url = ?, api_key_ref = ?,
                            model_type = ?, purpose = ?, enabled = ?, is_default = ?, description = ?, updated_at = ?
                        where id = ?
                        """,
                clean(request.name()), normalize(request.provider()), clean(request.modelName()), clean(request.baseUrl()),
                clean(request.apiKeyRef()), normalize(request.modelType()), clean(request.purpose()), request.enabled(),
                request.defaultModel(), clean(request.description()), Instant.now(), id);
        if (request.defaultModel()) {
            setDefault(id);
        } else if (existing.defaultModel()) {
            jdbcTemplate.update("update ai_model_registry set is_default = true where id = ?", id);
        }
        return require(id);
    }

    public AiModelRecord require(Long id) {
        try {
            return jdbcTemplate.queryForObject("""
                            select id, name, provider, model_name, base_url, api_key_ref, model_type, purpose,
                                   enabled, is_default, description, last_status, last_latency_ms, last_error,
                                   last_checked_at, created_at, updated_at
                            from ai_model_registry
                            where id = ?
                              and deleted = false
                            """,
                    (rs, rowNum) -> mapModel(rs),
                    id);
        } catch (EmptyResultDataAccessException ex) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "AI 模型不存在");
        }
    }

    public void setEnabled(Long id, boolean enabled) {
        require(id);
        jdbcTemplate.update("update ai_model_registry set enabled = ?, updated_at = ? where id = ?", enabled, Instant.now(), id);
    }

    @Transactional
    public void softDelete(Long id, String operator) {
        AiModelRecord model = require(id);
        if (model.defaultModel()) {
            throw new BusinessException(ErrorCode.CONFLICT, "默认模型不能删除，请先切换默认模型");
        }
        if (model.enabled()) {
            throw new BusinessException(ErrorCode.CONFLICT, "启用模型不能删除，请先停用模型");
        }
        jdbcTemplate.update("""
                        update ai_model_registry
                        set deleted = true, deleted_at = ?, deleted_by = ?, enabled = false, updated_at = ?
                        where id = ?
                        """,
                Instant.now(), clean(operator), Instant.now(), id);
    }

    @Transactional
    public void setDefault(Long id) {
        AiModelRecord model = require(id);
        if (!model.enabled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只能将已启用模型设为默认");
        }
        jdbcTemplate.update("update ai_model_registry set is_default = false where model_type = ?", model.modelType());
        jdbcTemplate.update("update ai_model_registry set is_default = true, updated_at = ? where id = ?", Instant.now(), id);
    }

    public String defaultModelName(String modelType, String fallback) {
        try {
            String value = jdbcTemplate.queryForObject("""
                            select model_name
                            from ai_model_registry
                            where model_type = ?
                              and enabled = true
                              and deleted = false
                              and is_default = true
                            order by id
                            limit 1
                            """,
                    String.class,
                    normalize(modelType));
            return value == null || value.isBlank() ? fallback : value;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public void recordCheck(Long id, String status, Long latencyMs, String error) {
        jdbcTemplate.update("""
                        update ai_model_registry
                        set last_status = ?, last_latency_ms = ?, last_error = ?, last_checked_at = ?, updated_at = ?
                        where id = ?
                        """,
                status, latencyMs, error, Instant.now(), Instant.now(), id);
    }

    private String normalize(String value) {
        return clean(value).toUpperCase(Locale.ROOT);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private AiModelRecord mapModel(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AiModelRecord(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("provider"),
                rs.getString("model_name"),
                rs.getString("base_url"),
                rs.getString("api_key_ref"),
                rs.getString("model_type"),
                rs.getString("purpose"),
                rs.getBoolean("enabled"),
                rs.getBoolean("is_default"),
                rs.getString("description"),
                rs.getString("last_status"),
                nullableLong(rs.getObject("last_latency_ms")),
                rs.getString("last_error"),
                instant(rs.getTimestamp("last_checked_at")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
