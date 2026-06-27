package weidonglang.tianshiwebside.governance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import weidonglang.tianshiwebside.common.error.BusinessException;
import weidonglang.tianshiwebside.common.error.ErrorCode;
import weidonglang.tianshiwebside.common.trace.TraceIdHolder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class ContentModerationService {
    private final JdbcTemplate jdbcTemplate;

    public ContentModerationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ModerationResult check(String scene, String content, String operator, boolean blockHighRisk) {
        return checkWithStrategy(scene, content, operator, blockHighRisk ? "legacy_block" : "legacy_record", false);
    }

    public ModerationResult checkConfigured(String scene, String content, String operator) {
        SafetyConfig config = safetyConfig(scene);
        if (config == null) {
            return checkWithStrategy(scene, content, operator, "block", false);
        }
        if (!config.enabled()) {
            String safeContent = content == null ? "" : content;
            log(config.scene(), safeContent, "", "LOW", "DISABLED", operator);
            return new ModerationResult(config.scene(), "", "LOW", "DISABLED");
        }
        return checkWithStrategy(config.scene(), content, operator, config.strategy(), true);
    }

    public List<SafetyConfig> safetyConfigs() {
        return jdbcTemplate.query("""
                        select id, scene, enabled, strategy, description, updated_at
                        from ai_safety_config
                        order by scene asc
                        """,
                (rs, rowNum) -> new SafetyConfig(
                        rs.getLong("id"),
                        rs.getString("scene"),
                        rs.getBoolean("enabled"),
                        rs.getString("strategy"),
                        rs.getString("description"),
                        rs.getObject("updated_at", Instant.class)
                ));
    }

    public SafetyConfig updateSafetyConfig(String scene, boolean enabled, String strategy, String description) {
        String normalizedScene = normalizeScene(scene);
        String normalizedStrategy = normalizeStrategy(strategy);
        String cleanDescription = description == null || description.isBlank() ? null : description.trim();
        Long count = jdbcTemplate.queryForObject("select count(*) from ai_safety_config where scene = ?", Long.class, normalizedScene);
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                            update ai_safety_config
                            set enabled = ?, strategy = ?, description = ?, updated_at = ?
                            where scene = ?
                            """,
                    enabled, normalizedStrategy, cleanDescription, Instant.now(), normalizedScene);
        } else {
            jdbcTemplate.update("""
                            insert into ai_safety_config (scene, enabled, strategy, description, updated_at)
                            values (?, ?, ?, ?, ?)
                            """,
                    normalizedScene, enabled, normalizedStrategy, cleanDescription, Instant.now());
        }
        return safetyConfig(normalizedScene);
    }

    public SafetyConfig safetyConfig(String scene) {
        List<SafetyConfig> rows = jdbcTemplate.query("""
                        select id, scene, enabled, strategy, description, updated_at
                        from ai_safety_config
                        where scene = ?
                        """,
                (rs, rowNum) -> new SafetyConfig(
                        rs.getLong("id"),
                        rs.getString("scene"),
                        rs.getBoolean("enabled"),
                        rs.getString("strategy"),
                        rs.getString("description"),
                        rs.getObject("updated_at", Instant.class)
                ),
                normalizeScene(scene));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private ModerationResult checkWithStrategy(String scene, String content, String operator, String strategy, boolean configurable) {
        String safeContent = content == null ? "" : content;
        List<SensitiveWordRow> words = jdbcTemplate.query("""
                        select id, word, category, risk_level, enabled, created_at, updated_at
                        from sensitive_word
                        where enabled = true
                        order by risk_level desc, word asc
                        """,
                (rs, rowNum) -> new SensitiveWordRow(
                        rs.getLong("id"),
                        rs.getString("word"),
                        rs.getString("category"),
                        rs.getString("risk_level"),
                        rs.getBoolean("enabled"),
                        rs.getObject("created_at", Instant.class),
                        rs.getObject("updated_at", Instant.class)
                )
        );
        List<SensitiveWordRow> matched = words.stream()
                .filter(word -> !word.word().isBlank() && safeContent.contains(word.word()))
                .toList();
        String riskLevel = matched.stream()
                .map(SensitiveWordRow::riskLevel)
                .max(Comparator.comparingInt(this::riskWeight))
                .orElse("LOW");
        String action = action(strategy, riskLevel, matched.isEmpty(), configurable);
        String matchedWords = String.join(",", matched.stream().map(SensitiveWordRow::word).toList());
        log(normalizeScene(scene), safeContent, matchedWords, riskLevel, action, operator);
        ModerationResult result = new ModerationResult(scene, matchedWords, riskLevel, action);
        if ("BLOCK".equals(action)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内容命中高风险敏感词，已拦截");
        }
        return result;
    }

    private String action(String strategy, String riskLevel, boolean noMatch, boolean configurable) {
        if (noMatch) {
            return "PASS";
        }
        if (!configurable) {
            return switch (strategy) {
                case "legacy_block" -> "HIGH".equals(riskLevel) ? "BLOCK" : "RECORD";
                case "legacy_record" -> "RECORD";
                default -> "HIGH".equals(riskLevel) ? "BLOCK" : "RECORD";
            };
        }
        String normalized = normalizeStrategy(strategy);
        return switch (normalized) {
            case "block" -> "BLOCK";
            case "warn" -> "WARN";
            case "review" -> "REVIEW";
            case "log_only" -> "LOG_ONLY";
            default -> "REVIEW";
        };
    }

    private void log(String scene, String content, String matchedWords, String riskLevel, String action, String operator) {
        jdbcTemplate.update("""
                        insert into content_moderation_log
                          (scene, content_hash, matched_words, risk_level, action, operator, trace_id, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                normalizeScene(scene),
                sha256(content),
                matchedWords,
                riskLevel,
                action,
                operator,
                TraceIdHolder.get(),
                Instant.now()
        );
    }

    private String normalizeScene(String scene) {
        return scene == null || scene.isBlank() ? "GENERAL" : scene.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeStrategy(String strategy) {
        if (strategy == null || strategy.isBlank()) {
            return "block";
        }
        String normalized = strategy.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "block", "warn", "review", "log_only" -> normalized;
            default -> "review";
        };
    }

    private int riskWeight(String riskLevel) {
        return switch (riskLevel) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    public record SensitiveWordRow(
            Long id,
            String word,
            String category,
            String riskLevel,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ModerationResult(
            String scene,
            String matchedWords,
            String riskLevel,
            String action
    ) {
    }

    public record SafetyConfig(
            Long id,
            String scene,
            boolean enabled,
            String strategy,
            String description,
            Instant updatedAt
    ) {
    }
}
