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
        auditLogMapper.insert(operator == null ? "anonymous" : operator, action, targetType,
                targetId == null ? null : String.valueOf(targetId), detail, traceId, Instant.now());
    }
}
