package weidonglang.tianshiwebside.audit.mapper;

import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.List;

@Mapper
public interface AuditLogMapper {
    @Insert("""
            insert into operation_audit_log (
              operator, action, target_type, target_id, detail, trace_id, created_at,
              module, risk_level, success_flag, failure_reason
            )
            values (
              #{operator}, #{action}, #{targetType}, #{targetId}, #{detail}, #{traceId}, #{createdAt},
              #{module}, #{riskLevel}, #{successFlag}, #{failureReason}
            )
            """)
    int insert(@Param("operator") String operator, @Param("action") String action,
               @Param("targetType") String targetType, @Param("targetId") String targetId,
               @Param("detail") String detail, @Param("traceId") String traceId,
               @Param("createdAt") Instant createdAt, @Param("module") String module,
               @Param("riskLevel") String riskLevel, @Param("successFlag") boolean successFlag,
               @Param("failureReason") String failureReason);

    @Select("""
            select id, operator, action, target_type as target_type, target_id as target_id,
                   detail, trace_id as trace_id, created_at as created_at,
                   module, risk_level as risk_level, success_flag as success_flag,
                   failure_reason as failure_reason
            from operation_audit_log
            where (#{keyword} is null or operator like #{keyword} or action like #{keyword} or target_type like #{keyword})
              and (#{riskLevel} is null or risk_level = #{riskLevel})
              and (#{module} is null or module = #{module})
            order by created_at desc
            limit #{size} offset #{offset}
            """)
    List<AuditLogRow> findLogs(@Param("keyword") String keyword, @Param("riskLevel") String riskLevel,
                               @Param("module") String module, @Param("size") int size, @Param("offset") int offset);

    @Select("""
            select count(*)
            from operation_audit_log
            where (#{keyword} is null or operator like #{keyword} or action like #{keyword} or target_type like #{keyword})
              and (#{riskLevel} is null or risk_level = #{riskLevel})
              and (#{module} is null or module = #{module})
            """)
    long countLogs(@Param("keyword") String keyword, @Param("riskLevel") String riskLevel, @Param("module") String module);

    record AuditLogRow(Long id, String operator, String action, String targetType, String targetId,
                       String detail, String traceId, Instant createdAt, String module,
                       String riskLevel, Boolean successFlag, String failureReason) {
    }
}
