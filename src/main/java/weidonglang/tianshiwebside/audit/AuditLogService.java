package weidonglang.tianshiwebside.audit;

import org.springframework.stereotype.Service;
import weidonglang.tianshiwebside.audit.mapper.AuditLogMapper;

import java.time.Instant;

@Service
public class AuditLogService {
    private final AuditLogMapper auditLogMapper;

    public AuditLogService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void record(String operator, String action, String targetType, Object targetId, String detail, String traceId) {
        record(operator, action, targetType, targetId, detail, traceId, true, null);
    }

    public void record(String operator, String action, String targetType, Object targetId, String detail,
                       String traceId, boolean success, String failureReason) {
        String normalizedAction = action == null ? "UNKNOWN" : action;
        String normalizedTargetType = targetType == null ? "UNKNOWN" : targetType;
        auditLogMapper.insert(operator == null ? "anonymous" : operator, normalizedAction, normalizedTargetType,
                targetId == null ? null : String.valueOf(targetId), detail, traceId, Instant.now(),
                moduleOf(normalizedTargetType), riskLevelOf(normalizedAction, normalizedTargetType),
                success, failureReason);
    }

    private String moduleOf(String targetType) {
        return switch (targetType.toUpperCase()) {
            case "GRADE" -> "GRADE";
            case "COURSE", "COURSE_OFFERING" -> "COURSE";
            case "STATUS_CHANGE", "REGISTRATION_APPLICATION" -> "APPLICATION";
            case "NOTICE" -> "NOTICE";
            case "ROLE", "USER" -> "PERMISSION";
            case "DATABASE", "AI_SQL" -> "DATABASE";
            default -> targetType.toUpperCase();
        };
    }

    private String riskLevelOf(String action, String targetType) {
        String normalized = (action + " " + targetType).toUpperCase();
        if (normalized.contains("DELETE")
                || normalized.contains("UPDATE_ROLE")
                || normalized.contains("RESET_PASSWORD")
                || normalized.contains("AI_SQL_EXECUTE")
                || normalized.contains("DATABASE")
                || normalized.contains("GRADE")) {
            return "HIGH";
        }
        if (normalized.contains("IMPORT")
                || normalized.contains("EXPORT")
                || normalized.contains("REVIEW")
                || normalized.contains("PUBLISH")
                || normalized.contains("COURSE_OFFERING")) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
