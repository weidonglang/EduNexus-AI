package weidonglang.tianshiwebside.common.error;

public enum ErrorCode {
    BAD_REQUEST("400", "Bad request"),
    UNAUTHORIZED("401", "Unauthorized"),
    FORBIDDEN("403", "Forbidden"),
    NOT_FOUND("404", "Not found"),
    CONFLICT("409", "Conflict"),
    AUTH_UNAUTHORIZED("AUTH_UNAUTHORIZED", "Unauthorized"),
    AUTH_FORBIDDEN("AUTH_FORBIDDEN", "Forbidden"),
    COURSE_CAPACITY_FULL("COURSE_CAPACITY_FULL", "教学班容量已满"),
    COURSE_TIME_CONFLICT("COURSE_TIME_CONFLICT", "课程时间冲突"),
    COURSE_DUPLICATE_SELECTION("COURSE_DUPLICATE_SELECTION", "重复选课"),
    COURSE_WINDOW_CLOSED("COURSE_WINDOW_CLOSED", "当前不在选课时间窗口内"),
    APPLICATION_INVALID_STATUS("APPLICATION_INVALID_STATUS", "申请状态不允许当前操作"),
    GRADE_LOCKED("GRADE_LOCKED", "成绩已锁定"),
    SQL_REJECTED("SQL_REJECTED", "SQL 被安全规则拦截"),
    AI_SERVICE_UNAVAILABLE("AI_SERVICE_UNAVAILABLE", "AI 服务不可用"),
    FILE_TYPE_NOT_ALLOWED("FILE_TYPE_NOT_ALLOWED", "文件类型不允许"),
    BATCH_PARTIAL_FAILURE("BATCH_PARTIAL_FAILURE", "批量操作部分失败"),
    INTERNAL_ERROR("500", "Internal server error");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
