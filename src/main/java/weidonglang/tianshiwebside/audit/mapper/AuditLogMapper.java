package weidonglang.tianshiwebside.audit.mapper;

import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.List;

@Mapper
public interface AuditLogMapper {
    @Insert("""
            insert into operation_audit_log (operator, action, target_type, target_id, detail, trace_id, created_at)
            values (#{operator}, #{action}, #{targetType}, #{targetId}, #{detail}, #{traceId}, #{createdAt})
            """)
    int insert(@Param("operator") String operator, @Param("action") String action,
               @Param("targetType") String targetType, @Param("targetId") String targetId,
               @Param("detail") String detail, @Param("traceId") String traceId,
               @Param("createdAt") Instant createdAt);

    @Select("""
            select id, operator, action, target_type as target_type, target_id as target_id,
                   detail, trace_id as trace_id, created_at as created_at
            from operation_audit_log
            where (#{keyword} is null or operator like #{keyword} or action like #{keyword} or target_type like #{keyword})
            order by created_at desc
            limit #{size} offset #{offset}
            """)
    List<AuditLogRow> findLogs(@Param("keyword") String keyword, @Param("size") int size, @Param("offset") int offset);

    @Select("""
            select count(*)
            from operation_audit_log
            where (#{keyword} is null or operator like #{keyword} or action like #{keyword} or target_type like #{keyword})
            """)
    long countLogs(@Param("keyword") String keyword);

    record AuditLogRow(Long id, String operator, String action, String targetType, String targetId,
                       String detail, String traceId, Instant createdAt) {
    }
}
