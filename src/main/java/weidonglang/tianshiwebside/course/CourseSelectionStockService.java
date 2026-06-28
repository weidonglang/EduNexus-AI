package weidonglang.tianshiwebside.course;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import weidonglang.tianshiwebside.audit.AuditLogService;
import weidonglang.tianshiwebside.common.trace.TraceIdHolder;

import java.time.Duration;

@Service
public class CourseSelectionStockService {
    private static final Duration STOCK_TTL = Duration.ofMinutes(30);

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final AuditLogService auditLogService;

    public CourseSelectionStockService(
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate redisTemplate,
            AuditLogService auditLogService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.auditLogService = auditLogService;
    }

    public StockRebuildResult rebuildFromDatabase(Long offeringId, String operator, String action) {
        if (offeringId == null) {
            return new StockRebuildResult(null, null, false, "offeringId is null");
        }
        try {
            Integer capacity = jdbcTemplate.queryForObject(
                    "select capacity from course_offering where id = ?",
                    Integer.class,
                    offeringId
            );
            Long selectedCount = jdbcTemplate.queryForObject(
                    "select count(*) from course_selection where offering_id = ?",
                    Long.class,
                    offeringId
            );
            int remaining = Math.max(0, (capacity == null ? 0 : capacity) - (selectedCount == null ? 0 : selectedCount.intValue()));
            redisTemplate.opsForValue().set(stockKey(offeringId), Integer.toString(remaining), STOCK_TTL);
            auditLogService.record(operator, action, "COURSE_OFFERING", offeringId,
                    "remaining=" + remaining, TraceIdHolder.get());
            return new StockRebuildResult(offeringId, remaining, true, "Redis stock rebuilt from database");
        } catch (RuntimeException ex) {
            String message = "Redis unavailable or stock rebuild failed; DB remains authoritative";
            auditLogService.record(operator, action + "_DEGRADED", "COURSE_OFFERING", offeringId,
                    message, TraceIdHolder.get(), false, ex.getMessage());
            return new StockRebuildResult(offeringId, null, false, message);
        }
    }

    public String stockKey(Long offeringId) {
        return "selection:offering:" + offeringId + ":remaining";
    }

    public record StockRebuildResult(Long offeringId, Integer remaining, boolean success, String message) {
    }
}
