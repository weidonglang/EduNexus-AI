package weidonglang.tianshiwebside.admin;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import weidonglang.tianshiwebside.audit.AuditLogService;
import weidonglang.tianshiwebside.common.api.ApiResponse;
import weidonglang.tianshiwebside.common.api.PageResponse;
import weidonglang.tianshiwebside.common.error.BusinessException;
import weidonglang.tianshiwebside.common.error.ErrorCode;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 数据库安全浏览接口。
 *
 * 这个模块只做“安全版”能力：查看连接信息、对象树、表结构、索引、外键、分页数据预览、
 * CSV 导出、ER 关系数据和历史操作记录。它不提供新增、修改、删除、任意 SQL 执行、
 * CREATE TABLE、ALTER TABLE、DROP TABLE 等高风险能力。
 */
@RestController
@RequestMapping("/api/admin/database-browser")
@PreAuthorize("hasRole('ADMIN')")
public class DatabaseBrowserController {
    private static final int MAX_PREVIEW_SIZE = 100;
    private static final int MAX_EXPORT_ROWS = 5000;
    private static final Set<String> SEARCHABLE_TYPES = Set.of(
            "char", "varchar", "tinytext", "text", "mediumtext", "longtext",
            "enum", "set", "json"
    );

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    public DatabaseBrowserController(JdbcTemplate jdbcTemplate, AuditLogService auditLogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
    }

    /**
     * 当前项目只连接一个演示数据库，因此这里展示“当前连接”信息。
     * 密码不会返回给前端，避免泄露敏感信息。
     */
    @GetMapping("/connection")
    public ApiResponse<ConnectionInfo> connection(Principal principal) {
        recordHistory(principal, "DB_BROWSER_CONNECTION", null, "view current connection");
        return ApiResponse.success(new ConnectionInfo(
                "当前教务系统数据库",
                "localhost",
                3306,
                currentSchema(),
                "MySQL",
                "只读安全连接，不展示密码"
        ));
    }

    /**
     * 查询表列表，支持分类和关键词过滤。
     */
    @GetMapping("/tables")
    public ApiResponse<List<TableInfo>> tables(
            Principal principal,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String keyword
    ) {
        String normalizedModule = normalizeBlank(module);
        String normalizedKeyword = normalizeBlank(keyword);
        List<TableInfo> result = loadTables().stream()
                .filter(row -> normalizedModule == null || row.module().equals(normalizedModule))
                .filter(row -> normalizedKeyword == null
                        || row.tableName().toLowerCase(Locale.ROOT).contains(normalizedKeyword.toLowerCase(Locale.ROOT))
                        || row.comment().toLowerCase(Locale.ROOT).contains(normalizedKeyword.toLowerCase(Locale.ROOT)))
                .toList();
        recordHistory(principal, "DB_BROWSER_TABLES", null, "module=" + normalizedModule + ", keyword=" + normalizedKeyword);
        return ApiResponse.success(result);
    }

    /**
     * 查询数据库对象树：数据库 -> 表 -> 字段/索引/视图。
     */
    @GetMapping("/tree")
    public ApiResponse<DatabaseTree> tree(Principal principal, @RequestParam(required = false) String keyword) {
        String normalizedKeyword = normalizeBlank(keyword);
        List<TableNode> tableNodes = loadTables().stream()
                .map(table -> new TableNode(
                        table.tableName(),
                        table.module(),
                        loadColumns(table.tableName()).stream()
                                .filter(column -> normalizedKeyword == null
                                        || table.tableName().toLowerCase(Locale.ROOT).contains(normalizedKeyword.toLowerCase(Locale.ROOT))
                                        || column.columnName().toLowerCase(Locale.ROOT).contains(normalizedKeyword.toLowerCase(Locale.ROOT)))
                                .map(column -> new TreeColumnNode(column.columnName(), column.columnType(), "PRI".equals(column.columnKey())))
                                .toList(),
                        loadIndexes(table.tableName()).stream()
                                .map(index -> new TreeIndexNode(index.indexName(), index.columnName(), index.uniqueIndex()))
                                .toList()
                ))
                .filter(table -> normalizedKeyword == null
                        || table.tableName().toLowerCase(Locale.ROOT).contains(normalizedKeyword.toLowerCase(Locale.ROOT))
                        || table.columns().stream().anyMatch(column -> column.columnName().toLowerCase(Locale.ROOT).contains(normalizedKeyword.toLowerCase(Locale.ROOT))))
                .toList();
        List<String> views = jdbcTemplate.queryForList("""
                        select table_name
                        from information_schema.views
                        where table_schema = ?
                        order by table_name
                        """,
                String.class,
                currentSchema()
        );
        recordHistory(principal, "DB_BROWSER_TREE", null, "keyword=" + normalizedKeyword);
        return ApiResponse.success(new DatabaseTree(currentSchema(), tableNodes, views));
    }

    /**
     * 查询字段结构。
     */
    @GetMapping("/tables/{tableName}/columns")
    public ApiResponse<List<ColumnInfo>> columns(Principal principal, @PathVariable String tableName) {
        requireExistingTable(tableName);
        recordHistory(principal, "DB_BROWSER_COLUMNS", tableName, "view columns");
        return ApiResponse.success(loadColumns(tableName));
    }

    /**
     * 查询索引结构。
     */
    @GetMapping("/tables/{tableName}/indexes")
    public ApiResponse<List<IndexInfo>> indexes(Principal principal, @PathVariable String tableName) {
        requireExistingTable(tableName);
        recordHistory(principal, "DB_BROWSER_INDEXES", tableName, "view indexes");
        return ApiResponse.success(loadIndexes(tableName));
    }

    /**
     * 查询外键关系。
     */
    @GetMapping("/tables/{tableName}/foreign-keys")
    public ApiResponse<List<ForeignKeyInfo>> foreignKeys(Principal principal, @PathVariable String tableName) {
        requireExistingTable(tableName);
        recordHistory(principal, "DB_BROWSER_FOREIGN_KEYS", tableName, "view foreign keys");
        return ApiResponse.success(loadForeignKeys(tableName));
    }

    /**
     * 分页预览表数据，支持关键词搜索和字段排序。
     */
    @GetMapping("/tables/{tableName}/preview")
    public ApiResponse<PageResponse<Map<String, Object>>> preview(
            Principal principal,
            @PathVariable String tableName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        requireExistingTable(tableName);
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(1, Math.min(size, MAX_PREVIEW_SIZE));
        String normalizedKeyword = normalizeBlank(keyword);
        QueryParts queryParts = buildKeywordQuery(tableName, normalizedKeyword);
        Long total = jdbcTemplate.queryForObject(
                "select count(*) from " + quoteIdentifier(tableName) + queryParts.whereClause(),
                Long.class,
                queryParts.args().toArray()
        );
        List<Object> args = new ArrayList<>(queryParts.args());
        args.add(safeSize);
        args.add((safePage - 1) * safeSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "select * from " + quoteIdentifier(tableName)
                        + queryParts.whereClause()
                        + buildOrderClause(tableName, sortBy, sortDir)
                        + " limit ? offset ?",
                args.toArray()
        ).stream().map(this::maskSensitiveColumns).toList();
        recordHistory(principal, "DB_BROWSER_PREVIEW", tableName,
                "page=" + safePage + ", size=" + safeSize + ", keyword=" + normalizedKeyword + ", sortBy=" + normalizeBlank(sortBy));
        return ApiResponse.success(new PageResponse<>(records, safePage, safeSize, total == null ? 0 : total));
    }

    /**
     * 导出当前表查询结果为 CSV，最多导出 5000 行。
     */
    @GetMapping(value = "/tables/{tableName}/export.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> exportCsv(
            Principal principal,
            @PathVariable String tableName,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        requireExistingTable(tableName);
        String normalizedKeyword = normalizeBlank(keyword);
        QueryParts queryParts = buildKeywordQuery(tableName, normalizedKeyword);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "select * from " + quoteIdentifier(tableName)
                        + queryParts.whereClause()
                        + buildOrderClause(tableName, sortBy, sortDir)
                        + " limit " + MAX_EXPORT_ROWS,
                queryParts.args().toArray()
        ).stream().map(this::maskSensitiveColumns).toList();
        String csv = toCsv(records, tableColumns(tableName));
        recordHistory(principal, "DB_BROWSER_EXPORT_CSV", tableName, "rows=" + records.size() + ", keyword=" + normalizedKeyword);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + tableName + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }

    /**
     * 查询 ER 图结构数据。
     */
    @GetMapping("/er")
    public ApiResponse<ErGraph> erGraph(Principal principal) {
        List<ErTableNode> nodes = loadTables().stream()
                .map(table -> new ErTableNode(
                        table.tableName(),
                        table.module(),
                        loadColumns(table.tableName()).stream()
                                .map(column -> new ErColumnNode(column.columnName(), column.columnType(), "PRI".equals(column.columnKey()), isForeignKey(table.tableName(), column.columnName())))
                                .toList()
                ))
                .toList();
        List<ErRelation> relations = loadTables().stream()
                .flatMap(table -> loadForeignKeys(table.tableName()).stream()
                        .map(fk -> new ErRelation(table.tableName(), fk.columnName(), fk.referencedTableName(), fk.referencedColumnName(), "多对一")))
                .toList();
        recordHistory(principal, "DB_BROWSER_ER", null, "tables=" + nodes.size() + ", relations=" + relations.size());
        return ApiResponse.success(new ErGraph(nodes, relations));
    }

    /**
     * 查询数据库浏览历史操作。
     */
    @GetMapping("/history")
    public ApiResponse<PageResponse<HistoryRow>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(1, Math.min(size, 100));
        List<HistoryRow> records = jdbcTemplate.query("""
                        select id, operator, action, target_type, target_id, detail, created_at
                        from operation_audit_log
                        where action like 'DB_BROWSER%'
                        order by created_at desc
                        limit ? offset ?
                        """,
                (rs, rowNum) -> new HistoryRow(
                        rs.getLong("id"),
                        rs.getString("operator"),
                        rs.getString("action"),
                        rs.getString("target_type"),
                        rs.getString("target_id"),
                        rs.getString("detail"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                safeSize,
                (safePage - 1) * safeSize
        );
        Long total = jdbcTemplate.queryForObject("select count(*) from operation_audit_log where action like 'DB_BROWSER%'", Long.class);
        return ApiResponse.success(new PageResponse<>(records, safePage, safeSize, total == null ? 0 : total));
    }

    /**
     * Database visualization dashboard data.
     * This endpoint only reads MySQL metadata and audit logs. It is used by the
     * frontend ECharts dashboard and does not expose SQL execution or write APIs.
     */
    @GetMapping("/dashboard")
    public ApiResponse<DatabaseDashboard> dashboard(Principal principal) {
        String schema = currentSchema();
        List<TableInfo> tableInfos = loadTables();
        long totalRows = tableInfos.stream().mapToLong(TableInfo::rowCount).sum();
        long fieldCount = countMetadata("""
                select count(*)
                from information_schema.columns
                where table_schema = ?
                """, schema);
        long indexCount = countMetadata("""
                select count(*)
                from information_schema.statistics
                where table_schema = ?
                """, schema);
        long foreignKeyCount = countMetadata("""
                select count(*)
                from information_schema.key_column_usage
                where table_schema = ?
                  and referenced_table_name is not null
                """, schema);
        long recentSqlCount = countMetadata("""
                select count(*)
                from operation_audit_log
                where action like 'DB_BROWSER%'
                  and created_at >= date_sub(now(), interval 1 day)
                """);
        List<NameValue> tableRows = tableInfos.stream()
                .map(table -> new NameValue(table.tableName(), table.rowCount()))
                .toList();
        List<NameValue> fieldTypes = jdbcTemplate.query("""
                        select data_type, count(*) as amount
                        from information_schema.columns
                        where table_schema = ?
                        group by data_type
                        order by amount desc
                        """,
                (rs, rowNum) -> new NameValue(rs.getString("data_type"), rs.getLong("amount")),
                schema
        );
        List<NameValue> sqlTrend = jdbcTemplate.query("""
                        select date_format(created_at, '%m-%d %H:00') as label, count(*) as amount, min(created_at) as first_time
                        from operation_audit_log
                        where action like 'DB_BROWSER%'
                        group by date_format(created_at, '%m-%d %H:00')
                        order by first_time desc
                        limit 12
                        """,
                (rs, rowNum) -> new NameValue(rs.getString("label"), rs.getLong("amount"))
        );
        List<NameValue> actionRanking = jdbcTemplate.query("""
                        select action, count(*) as amount
                        from operation_audit_log
                        where action like 'DB_BROWSER%'
                        group by action
                        order by amount desc
                        limit 8
                        """,
                (rs, rowNum) -> new NameValue(rs.getString("action"), rs.getLong("amount"))
        );
        List<NameValue> importQuality = List.of(
                new NameValue("Valid rows", Math.max(totalRows, 0)),
                new NameValue("Empty cells", 0),
                new NameValue("Duplicate rows", 0),
                new NameValue("Abnormal rows", 0)
        );
        Collections.reverse(sqlTrend);
        DatabaseDashboard dashboard = new DatabaseDashboard(
                new DashboardStats(tableInfos.size(), fieldCount, indexCount, foreignKeyCount, totalRows, recentSqlCount, 100.0),
                tableRows,
                fieldTypes,
                sqlTrend,
                List.of(new NameValue("Success", recentSqlCount), new NameValue("Failure", 0)),
                actionRanking,
                importQuality
        );
        recordHistory(principal, "DB_BROWSER_DASHBOARD", null, "tables=" + tableInfos.size() + ", rows=" + totalRows);
        return ApiResponse.success(dashboard);
    }

    @GetMapping("/templates")
    public ApiResponse<List<QueryTemplate>> templates() {
        return ApiResponse.success(List.of(
                new QueryTemplate("course-students", "查询某课程的所有选课学生", "课程选课", List.of("keyword")),
                new QueryTemplate("student-grades", "查询某学生的成绩与绩点", "成绩考试", List.of("keyword")),
                new QueryTemplate("offering-exam", "查询某教学班的考试安排", "成绩考试", List.of("keyword")),
                new QueryTemplate("class-students", "查询某班级学生名单", "学生学籍", List.of("keyword")),
                new QueryTemplate("term-selection-stats", "查询某学期选课人数统计", "课程选课", List.of("term")),
                new QueryTemplate("ai-failed-logs", "查询 AI 调用失败记录", "AI", List.of())
        ));
    }

    @GetMapping("/templates/{templateCode}/run")
    public ApiResponse<PageResponse<Map<String, Object>>> runTemplate(
            Principal principal,
            @PathVariable String templateCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String term,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PREVIEW_SIZE));
        TemplateQuery query = templateQuery(templateCode, normalizeBlank(keyword), normalizeBlank(term));
        List<Object> pageArgs = new ArrayList<>(query.args());
        pageArgs.add(safeSize);
        pageArgs.add((safePage - 1) * safeSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(query.sql() + " limit ? offset ?", pageArgs.toArray())
                .stream()
                .map(this::maskSensitiveColumns)
                .toList();
        Long total = jdbcTemplate.queryForObject("select count(*) from (" + query.sql() + ") t", Long.class, query.args().toArray());
        recordHistory(principal, "DB_BROWSER_TEMPLATE_QUERY", templateCode,
                "keyword=" + normalizeBlank(keyword) + ", term=" + normalizeBlank(term));
        return ApiResponse.success(new PageResponse<>(records, safePage, safeSize, total == null ? 0 : total));
    }

    @GetMapping(value = "/templates/{templateCode}/export.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> exportTemplateCsv(
            Principal principal,
            @PathVariable String templateCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String term
    ) {
        TemplateQuery query = templateQuery(templateCode, normalizeBlank(keyword), normalizeBlank(term));
        List<Map<String, Object>> records = jdbcTemplate.queryForList(query.sql() + " limit " + MAX_EXPORT_ROWS, query.args().toArray())
                .stream()
                .map(this::maskSensitiveColumns)
                .toList();
        List<String> headers = records.isEmpty() ? List.of("empty") : new ArrayList<>(records.get(0).keySet());
        recordHistory(principal, "DB_BROWSER_TEMPLATE_EXPORT", templateCode,
                "rows=" + records.size() + ", keyword=" + normalizeBlank(keyword) + ", term=" + normalizeBlank(term));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + templateCode + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(toCsv(records, headers));
    }

    private TemplateQuery templateQuery(String templateCode, String keyword, String term) {
        String like = "%" + (keyword == null ? "" : keyword) + "%";
        return switch (templateCode) {
            case "course-students" -> new TemplateQuery("""
                    select co.id as offering_id, c.code as course_code, c.name as course_name,
                           co.teacher_name, s.student_no, u.display_name as student_name, s.class_name
                    from course_selection cs
                    join course_offering co on co.id = cs.offering_id
                    join course c on c.id = co.course_id
                    join student s on s.id = cs.student_id
                    join sys_user u on u.id = s.user_id
                    where c.code like ? or c.name like ? or co.teacher_name like ?
                    order by co.id desc, s.student_no asc
                    """, List.of(like, like, like));
            case "student-grades" -> new TemplateQuery("""
                    select s.student_no, u.display_name as student_name, c.code as course_code,
                           c.name as course_name, g.score, g.grade_point, g.term
                    from academic_grade g
                    join student s on s.id = g.student_id
                    join sys_user u on u.id = s.user_id
                    join course c on c.id = g.course_id
                    where s.student_no like ? or u.display_name like ?
                    order by g.term desc, c.code asc
                    """, List.of(like, like));
            case "offering-exam" -> new TemplateQuery("""
                    select e.id as exam_id, co.id as offering_id, c.code as course_code, c.name as course_name,
                           co.teacher_name, e.exam_time, e.room, e.seat_no
                    from exam_schedule e
                    join course_offering co on co.id = e.course_offering_id
                    join course c on c.id = co.course_id
                    where concat('', co.id) like ? or c.code like ? or c.name like ?
                    order by e.exam_time desc
                    """, List.of(like, like, like));
            case "class-students" -> new TemplateQuery("""
                    select s.student_no, u.display_name as student_name, s.college, s.major, s.grade, s.class_name, s.status
                    from student s
                    join sys_user u on u.id = s.user_id
                    where s.class_name like ? or s.major like ? or s.grade like ?
                    order by s.class_name asc, s.student_no asc
                    """, List.of(like, like, like));
            case "term-selection-stats" -> new TemplateQuery("""
                    select co.term, c.code as course_code, c.name as course_name, co.teacher_name,
                           count(cs.id) as selected_count, max(co.capacity) as capacity
                    from course_offering co
                    join course c on c.id = co.course_id
                    left join course_selection cs on cs.offering_id = co.id
                    where co.term like ?
                    group by co.term, c.code, c.name, co.teacher_name
                    order by co.term desc, selected_count desc
                    """, List.of("%" + (term == null ? "" : term) + "%"));
            case "ai-failed-logs" -> new TemplateQuery("""
                    select id, username, function_type, model_name, service_mode, level, error_message, trace_id, created_at
                    from ai_call_log
                    where success = false or level = 'ERROR'
                    order by created_at desc
                    """, List.of());
            default -> throw new BusinessException(ErrorCode.NOT_FOUND, "查询模板不存在: " + templateCode);
        };
    }

    private List<TableInfo> loadTables() {
        return jdbcTemplate.query("""
                        select table_name, coalesce(table_comment, '') as table_comment, create_time, update_time
                        from information_schema.tables
                        where table_schema = ?
                          and table_type = 'BASE TABLE'
                        order by table_name
                        """,
                (rs, rowNum) -> {
                    String tableName = rs.getString("table_name");
                    return new TableInfo(
                            tableName,
                            moduleOf(tableName),
                            rs.getString("table_comment"),
                            countRows(tableName),
                            rs.getObject("create_time"),
                            rs.getObject("update_time")
                    );
                },
                currentSchema()
        );
    }

    private List<ColumnInfo> loadColumns(String tableName) {
        return jdbcTemplate.query("""
                        select column_name, column_type, data_type, is_nullable, column_key,
                               column_default, extra, coalesce(column_comment, '') as column_comment,
                               ordinal_position
                        from information_schema.columns
                        where table_schema = ?
                          and table_name = ?
                        order by ordinal_position
                        """,
                (rs, rowNum) -> new ColumnInfo(
                        rs.getString("column_name"),
                        rs.getString("column_type"),
                        rs.getString("data_type"),
                        rs.getString("is_nullable"),
                        rs.getString("column_key"),
                        rs.getString("column_default"),
                        rs.getString("extra"),
                        rs.getString("column_comment"),
                        rs.getInt("ordinal_position")
                ),
                currentSchema(),
                tableName
        );
    }

    private List<IndexInfo> loadIndexes(String tableName) {
        return jdbcTemplate.query("""
                        select index_name, column_name, non_unique, seq_in_index, index_type
                        from information_schema.statistics
                        where table_schema = ?
                          and table_name = ?
                        order by index_name, seq_in_index
                        """,
                (rs, rowNum) -> new IndexInfo(
                        rs.getString("index_name"),
                        rs.getString("column_name"),
                        !rs.getBoolean("non_unique"),
                        rs.getInt("seq_in_index"),
                        rs.getString("index_type")
                ),
                currentSchema(),
                tableName
        );
    }

    private List<ForeignKeyInfo> loadForeignKeys(String tableName) {
        return jdbcTemplate.query("""
                        select k.constraint_name, k.column_name, k.referenced_table_name, k.referenced_column_name,
                               rc.update_rule, rc.delete_rule
                        from information_schema.key_column_usage k
                        join information_schema.referential_constraints rc
                          on rc.constraint_schema = k.constraint_schema
                         and rc.constraint_name = k.constraint_name
                        where k.table_schema = ?
                          and k.table_name = ?
                          and k.referenced_table_name is not null
                        order by k.constraint_name, k.ordinal_position
                        """,
                (rs, rowNum) -> new ForeignKeyInfo(
                        rs.getString("constraint_name"),
                        rs.getString("column_name"),
                        rs.getString("referenced_table_name"),
                        rs.getString("referenced_column_name"),
                        rs.getString("update_rule"),
                        rs.getString("delete_rule")
                ),
                currentSchema(),
                tableName
        );
    }

    private String currentSchema() {
        return jdbcTemplate.queryForObject("select database()", String.class);
    }

    private long countMetadata(String sql, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        return count == null ? 0 : count;
    }

    private void requireExistingTable(String tableName) {
        String normalized = normalizeBlank(tableName);
        if (normalized == null || !normalized.matches("[A-Za-z0-9_]+")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "表名不合法");
        }
        Integer count = jdbcTemplate.queryForObject("""
                        select count(*)
                        from information_schema.tables
                        where table_schema = ?
                          and table_name = ?
                          and table_type = 'BASE TABLE'
                        """,
                Integer.class,
                currentSchema(),
                normalized
        );
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "表不存在: " + tableName);
        }
    }

    private long countRows(String tableName) {
        Long count = jdbcTemplate.queryForObject("select count(*) from " + quoteIdentifier(tableName), Long.class);
        return count == null ? 0 : count;
    }

    private QueryParts buildKeywordQuery(String tableName, String keyword) {
        if (keyword == null) {
            return new QueryParts("", List.of());
        }
        List<String> searchableColumns = jdbcTemplate.query("""
                        select column_name, data_type
                        from information_schema.columns
                        where table_schema = ?
                          and table_name = ?
                        order by ordinal_position
                        """,
                (rs, rowNum) -> new ColumnType(rs.getString("column_name"), rs.getString("data_type")),
                currentSchema(),
                tableName
        ).stream()
                .filter(column -> SEARCHABLE_TYPES.contains(column.dataType().toLowerCase(Locale.ROOT)))
                .map(ColumnType::columnName)
                .toList();
        if (searchableColumns.isEmpty()) {
            return new QueryParts("", List.of());
        }
        List<String> clauses = searchableColumns.stream().map(column -> quoteIdentifier(column) + " like ?").toList();
        List<Object> args = searchableColumns.stream().map(column -> "%" + keyword + "%").map(Object.class::cast).toList();
        return new QueryParts(" where " + String.join(" or ", clauses), args);
    }

    private String buildOrderClause(String tableName, String sortBy, String sortDir) {
        String normalizedSortBy = normalizeBlank(sortBy);
        if (normalizedSortBy == null) {
            return "";
        }
        if (!tableColumns(tableName).contains(normalizedSortBy)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "排序字段不存在: " + sortBy);
        }
        return " order by " + quoteIdentifier(normalizedSortBy) + ("desc".equalsIgnoreCase(sortDir) ? " desc" : " asc");
    }

    private List<String> tableColumns(String tableName) {
        return jdbcTemplate.queryForList("""
                        select column_name
                        from information_schema.columns
                        where table_schema = ?
                          and table_name = ?
                        order by ordinal_position
                        """,
                String.class,
                currentSchema(),
                tableName
        );
    }

    private boolean isForeignKey(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                        select count(*)
                        from information_schema.key_column_usage
                        where table_schema = ?
                          and table_name = ?
                          and column_name = ?
                          and referenced_table_name is not null
                        """,
                Integer.class,
                currentSchema(),
                tableName,
                columnName
        );
        return count != null && count > 0;
    }

    private Map<String, Object> maskSensitiveColumns(Map<String, Object> row) {
        Map<String, Object> masked = new LinkedHashMap<>();
        row.forEach((key, value) -> {
            String lowerKey = key.toLowerCase(Locale.ROOT);
            if (lowerKey.contains("password") || lowerKey.contains("token")) {
                masked.put(key, value == null ? null : "******");
            } else {
                masked.put(key, value);
            }
        });
        return masked;
    }

    private String toCsv(List<Map<String, Object>> records, List<String> headers) {
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append(String.join(",", headers.stream().map(this::escapeCsv).toList())).append("\n");
        for (Map<String, Object> row : records) {
            builder.append(String.join(",", headers.stream()
                    .map(header -> escapeCsv(row.get(header) == null ? "" : String.valueOf(row.get(header))))
                    .toList())).append("\n");
        }
        return builder.toString();
    }

    private String escapeCsv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void recordHistory(Principal principal, String action, String tableName, String detail) {
        auditLogService.record(principal == null ? "anonymous" : principal.getName(), action, "DATABASE", tableName, detail, null);
    }

    private String moduleOf(String tableName) {
        if (tableName.startsWith("sys_")) return "权限与用户";
        if (tableName.startsWith("course") || tableName.equals("teaching_plan_item")) return "课程选课";
        if (tableName.startsWith("academic") || tableName.startsWith("exam") || tableName.equals("classroom")) return "成绩考试";
        if (tableName.startsWith("student") || tableName.contains("registration")) return "学生学籍";
        if (tableName.contains("notice") || tableName.contains("notification")) return "通知公告";
        if (tableName.contains("evaluation")) return "教学评价";
        if (tableName.contains("audit") || tableName.contains("attachment") || tableName.contains("file")) return "审计文件";
        if (tableName.contains("feedback") || tableName.contains("warning") || tableName.contains("graduation") || tableName.contains("thesis")) return "信息服务";
        return "其他";
    }

    public record ConnectionInfo(String name, String host, int port, String databaseName, String type, String remark) {
    }

    public record TableInfo(String tableName, String module, String comment, long rowCount, Object createTime, Object updateTime) {
    }

    public record ColumnInfo(String columnName, String columnType, String dataType, String nullable, String columnKey,
                             String defaultValue, String extra, String comment, int ordinalPosition) {
    }

    public record IndexInfo(String indexName, String columnName, boolean uniqueIndex, int sequence, String indexType) {
    }

    public record ForeignKeyInfo(String constraintName, String columnName, String referencedTableName,
                                 String referencedColumnName, String updateRule, String deleteRule) {
    }

    public record DatabaseTree(String databaseName, List<TableNode> tables, List<String> views) {
    }

    public record TableNode(String tableName, String module, List<TreeColumnNode> columns, List<TreeIndexNode> indexes) {
    }

    public record TreeColumnNode(String columnName, String columnType, boolean primaryKey) {
    }

    public record TreeIndexNode(String indexName, String columnName, boolean uniqueIndex) {
    }

    public record ErGraph(List<ErTableNode> nodes, List<ErRelation> relations) {
    }

    public record ErTableNode(String tableName, String module, List<ErColumnNode> columns) {
    }

    public record ErColumnNode(String columnName, String columnType, boolean primaryKey, boolean foreignKey) {
    }

    public record ErRelation(String sourceTable, String sourceColumn, String targetTable, String targetColumn, String label) {
    }

    public record HistoryRow(Long id, String operator, String action, String targetType, String targetId, String detail, Instant createdAt) {
    }

    public record DatabaseDashboard(DashboardStats stats, List<NameValue> tableRows, List<NameValue> fieldTypes,
                                    List<NameValue> sqlTrend, List<NameValue> sqlStatus,
                                    List<NameValue> actionRanking, List<NameValue> importQuality) {
    }

    public record DashboardStats(long tableCount, long fieldCount, long indexCount, long foreignKeyCount,
                                 long totalRows, long recentSqlCount, double sqlSuccessRate) {
    }

    public record NameValue(String name, Number value) {
    }

    public record QueryTemplate(String code, String title, String module, List<String> parameters) {
    }

    private record ColumnType(String columnName, String dataType) {
    }

    private record QueryParts(String whereClause, List<Object> args) {
    }

    private record TemplateQuery(String sql, List<Object> args) {
    }
}
