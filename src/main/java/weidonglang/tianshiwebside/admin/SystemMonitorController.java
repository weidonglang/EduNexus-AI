package weidonglang.tianshiwebside.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import weidonglang.tianshiwebside.ai.AiRemoteClient;
import weidonglang.tianshiwebside.ai.AiServiceStatusResponse;
import weidonglang.tianshiwebside.common.api.ApiResponse;
import weidonglang.tianshiwebside.common.api.PageResponse;
import weidonglang.tianshiwebside.common.api.Pagination;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class SystemMonitorController {
    private static final DateTimeFormatter DISPLAY_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final StringRedisTemplate redisTemplate;
    private final AiRemoteClient aiRemoteClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JdbcTemplate jdbcTemplate;
    private final Path reportDir;
    private final Path uploadRoot;

    public SystemMonitorController(
            StringRedisTemplate redisTemplate,
            AiRemoteClient aiRemoteClient,
            JdbcTemplate jdbcTemplate,
            @Value("${load-test.report-dir:reports}") String reportDir,
            @Value("${app.upload-root:uploads}") String uploadRoot
    ) {
        this.redisTemplate = redisTemplate;
        this.aiRemoteClient = aiRemoteClient;
        this.jdbcTemplate = jdbcTemplate;
        this.reportDir = Path.of(reportDir).toAbsolutePath().normalize();
        this.uploadRoot = Path.of(uploadRoot).toAbsolutePath().normalize();
    }

    @GetMapping("/system-health")
    /**
     * 功能：聚合系统健康状态。
     * 说明：管理端健康中心用本接口展示 MySQL、Redis、AI Service、Ollama、
     * 上传目录、Flyway 和 JVM 状态，便于答辩时说明系统可观测性和降级情况。
     */
    public ApiResponse<SystemHealthResponse> systemHealth() {
        List<SystemHealthItem> items = List.of(
                mysqlHealth(),
                redisHealth(),
                aiHealth(),
                uploadHealth(),
                flywayHealth(),
                jvmHealth()
        );
        return ApiResponse.success(new SystemHealthResponse(
                Instant.now(),
                overallStatus(items),
                items,
                jvmMetrics()
        ));
    }

    @GetMapping("/redis-monitor")
    /**
     * 功能：查询 Redis 运行状态和缓存数据。
     * 说明：管理端 Redis 状态监控页调用本接口，返回 PING、key 总数、TTL、
     * selection:* 缓存列表，以及教学班库存和数据库容量的对比结果。
     */
    public ApiResponse<RedisMonitorResponse> redisMonitor(
            @RequestParam(defaultValue = "selection:*") String pattern,
            @RequestParam(defaultValue = "100") int limit
    ) {
        long startedAt = System.currentTimeMillis();
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        try {
            String ping;
            Long dbSize;
            try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
                ping = connection.ping();
                dbSize = connection.dbSize();
            }
            List<RedisKeyRow> keys = scanKeys(pattern, safeLimit);
            List<RedisStockCheckRow> stockChecks = buildStockChecks(keys, 20);
            return ApiResponse.success(new RedisMonitorResponse(
                    true,
                    ping,
                    pattern,
                    dbSize == null ? 0 : dbSize,
                    keys.size(),
                    System.currentTimeMillis() - startedAt,
                    keys,
                    stockChecks,
                    null
            ));
        } catch (RuntimeException ex) {
            return ApiResponse.success(new RedisMonitorResponse(
                    false,
                    "ERROR",
                    pattern,
                    0,
                    0,
                    System.currentTimeMillis() - startedAt,
                    List.of(),
                    List.of(),
                    ex.getClass().getSimpleName() + ": " + ex.getMessage()
            ));
        }
    }

    @GetMapping("/load-test-reports")
    /**
     * 功能：查询压测历史报告列表。
     * 说明：读取 reports 目录下的 load-test JSON 报告，提取请求数、成功数、
     * Redis 状态、吞吐量和延迟指标，供管理端历史报告页面展示。
     */
    public ApiResponse<PageResponse<LoadTestReportRow>> loadTestReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) throws IOException {
        if (!Files.isDirectory(reportDir)) {
            int safePage = Pagination.safePage(page);
            int safeSize = Pagination.safeSize(size);
            return ApiResponse.success(new PageResponse<>(List.of(), safePage, safeSize, 0));
        }
        List<LoadTestReportRow> rows = new ArrayList<>();
        try (var stream = Files.list(reportDir)) {
            List<Path> jsonFiles = stream
                    .filter(path -> path.getFileName().toString().startsWith("load-test-"))
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong(this::lastModifiedMillis).reversed())
                    .toList();
            for (Path jsonFile : jsonFiles) {
                rows.add(readReportRow(jsonFile));
            }
        }
        return ApiResponse.success(Pagination.slice(rows, page, size));
    }

    @PostMapping("/redis-monitor/prewarm-stock")
    /**
     * 功能：预热 Redis 教学班库存。
     * 说明：根据数据库中的教学班容量和已选人数计算剩余名额，写入
     * selection:offering:{offeringId}:remaining，便于抢课开始前 Redis 已经具备库存数据。
     */
    public ApiResponse<PrewarmStockResponse> prewarmStock(
            @RequestParam(defaultValue = "20") int limit
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        List<OfferingStockRow> offerings = queryOfferingStockRows(safeLimit);
        List<PrewarmStockItem> items = offerings.stream()
                .map(row -> {
                    long remaining = Math.max(0, row.capacity() - row.selected());
                    String key = stockKey(row.offeringId());
                    // 管理端手动预热库存：把数据库中的剩余名额写入 Redis 库存 key。
                    // key 格式为 selection:offering:{offeringId}:remaining。
                    redisTemplate.opsForValue().set(key, Long.toString(remaining), java.time.Duration.ofMinutes(30));
                    return new PrewarmStockItem(row.offeringId(), key, remaining);
                })
                .toList();
        return ApiResponse.success(new PrewarmStockResponse(items.size(), items));
    }

    @GetMapping(value = "/load-test-reports/{fileName:.+}/html", produces = MediaType.TEXT_HTML_VALUE)
    /**
     * 功能：打开压测 HTML 报告。
     * 说明：管理端报告列表点击查看时调用，后端只允许读取 reports 目录中的 html 文件，
     * 防止通过文件名参数访问目录外文件。
     */
    public ResponseEntity<Resource> loadTestReportHtml(@PathVariable String fileName) {
        if (!fileName.endsWith(".html") || fileName.contains("/") || fileName.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }
        Path htmlPath = reportDir.resolve(fileName).normalize();
        if (!htmlPath.startsWith(reportDir) || !Files.isRegularFile(htmlPath)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .contentType(MediaType.TEXT_HTML)
                .body(new FileSystemResource(htmlPath));
    }

    private List<RedisKeyRow> scanKeys(String pattern, int limit) {
        ScanOptions options = ScanOptions.scanOptions()
                .match((pattern == null || pattern.isBlank()) ? "selection:*" : pattern.trim())
                .count(100)
                .build();
        List<RedisKeyRow> keys = new ArrayList<>();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext() && keys.size() < limit) {
                String key = cursor.next();
                Long ttl = redisTemplate.getExpire(key);
                String value = redisTemplate.opsForValue().get(key);
                keys.add(new RedisKeyRow(key, ttl == null ? -2 : ttl, value));
            }
        }
        keys.sort(Comparator.comparing(RedisKeyRow::key));
        return keys;
    }

    private List<RedisStockCheckRow> buildStockChecks(List<RedisKeyRow> keys, int limit) {
        List<Long> redisOfferingIds = keys.stream()
                .map(RedisKeyRow::key)
                // 只把 selection:offering:{offeringId}:remaining 识别为“教学班库存 key”。
                .filter(key -> key.startsWith("selection:offering:") && key.endsWith(":remaining"))
                .map(this::parseOfferingId)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        List<Long> offeringIds = new ArrayList<>(redisOfferingIds);
        for (OfferingStockRow row : queryOfferingStockRows(limit)) {
            if (!offeringIds.contains(row.offeringId())) {
                offeringIds.add(row.offeringId());
            }
        }
        return offeringIds.stream().limit(limit)
                .map(offeringId -> buildStockCheckRow(keys, offeringId))
                .toList();
    }

    private RedisStockCheckRow buildStockCheckRow(List<RedisKeyRow> keys, Long offeringId) {
        String key = stockKey(offeringId);
        RedisKeyRow keyRow = keys.stream()
                .filter(item -> item.key().equals(key))
                .findFirst()
                .orElseGet(() -> readRedisKey(key));
        OfferingStockRow stock = queryOfferingStockRow(offeringId);
        int safeCapacity = stock == null ? 0 : stock.capacity();
        int safeSelected = stock == null ? 0 : stock.selected();
        Integer redisRemaining = parseInteger(keyRow == null ? null : keyRow.value());
        // 是否超卖以数据库最终数据为准：已选人数大于容量即判定超卖。
        // Redis 剩余库存用于展示缓存状态，数据库容量和已选人数用于最终核对。
        boolean oversold = safeSelected > safeCapacity;
        return new RedisStockCheckRow(
                offeringId,
                key,
                redisRemaining,
                safeCapacity,
                safeSelected,
                Math.max(0, safeCapacity - safeSelected),
                oversold
        );
    }

    private RedisKeyRow readRedisKey(String key) {
        try {
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                return null;
            }
            Long ttl = redisTemplate.getExpire(key);
            String value = redisTemplate.opsForValue().get(key);
            return new RedisKeyRow(key, ttl == null ? -2 : ttl, value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private List<OfferingStockRow> queryOfferingStockRows(int limit) {
        return jdbcTemplate.query("""
                        select
                          co.id as offering_id,
                          co.capacity as capacity,
                          count(cs.id) as selected
                        from course_offering co
                        left join course_selection cs on cs.offering_id = co.id
                        group by co.id, co.capacity
                        order by co.id
                        limit ?
                        """,
                (rs, rowNum) -> new OfferingStockRow(
                        rs.getLong("offering_id"),
                        rs.getInt("capacity"),
                        rs.getInt("selected")
                ),
                limit
        );
    }

    private OfferingStockRow queryOfferingStockRow(Long offeringId) {
        List<OfferingStockRow> rows = jdbcTemplate.query("""
                        select
                          co.id as offering_id,
                          co.capacity as capacity,
                          count(cs.id) as selected
                        from course_offering co
                        left join course_selection cs on cs.offering_id = co.id
                        where co.id = ?
                        group by co.id, co.capacity
                        """,
                (rs, rowNum) -> new OfferingStockRow(
                        rs.getLong("offering_id"),
                        rs.getInt("capacity"),
                        rs.getInt("selected")
                ),
                offeringId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String stockKey(Long offeringId) {
        // Redis 监控页和抢课服务使用同一套库存 key 格式，便于页面直接定位教学班库存。
        return "selection:offering:" + offeringId + ":remaining";
    }

    private long parseOfferingId(String key) {
        try {
            String value = key.substring("selection:offering:".length(), key.length() - ":remaining".length());
            return Long.parseLong(value);
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    private Integer parseInteger(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private SystemHealthItem mysqlHealth() {
        long start = System.nanoTime();
        try {
            Integer value = jdbcTemplate.queryForObject("select 1", Integer.class);
            return new SystemHealthItem(
                    "mysql",
                    "MySQL 数据库",
                    value != null && value == 1 ? "UP" : "DEGRADED",
                    "数据库连接和只读探活正常",
                    elapsedMillis(start),
                    Map.of("probe", value == null ? "" : value)
            );
        } catch (RuntimeException ex) {
            return failedHealth("mysql", "MySQL 数据库", "数据库连接失败", start, ex);
        }
    }

    private SystemHealthItem redisHealth() {
        long start = System.nanoTime();
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            String ping = connection.ping();
            Long dbSize = connection.dbSize();
            return new SystemHealthItem(
                    "redis",
                    "Redis 缓存",
                    "PONG".equalsIgnoreCase(ping) ? "UP" : "DEGRADED",
                    "Redis 可连接，缓存和抢课库存可用",
                    elapsedMillis(start),
                    Map.of(
                            "ping", ping == null ? "" : ping,
                            "dbSize", dbSize == null ? 0 : dbSize
                    )
            );
        } catch (RuntimeException ex) {
            return failedHealth("redis", "Redis 缓存", "Redis 不可用，业务会降级到数据库兜底", start, ex);
        }
    }

    private SystemHealthItem aiHealth() {
        AiServiceStatusResponse status = aiRemoteClient.status();
        String health = status.aiServiceOnline()
                ? (status.ollamaReachable() ? "UP" : "DEGRADED")
                : "DEGRADED";
        String detail = status.aiServiceOnline()
                ? status.currentMode()
                : "AI Service 不可达，主系统使用本地兜底";
        return new SystemHealthItem(
                "ai",
                "AI Service / Ollama",
                health,
                detail,
                status.lastLatencyMs(),
                Map.of(
                        "aiServiceReachable", status.aiServiceOnline(),
                        "ollamaEnabled", status.ollamaEnabled(),
                        "ollamaReachable", status.ollamaReachable(),
                        "chatModel", status.chatModel(),
                        "sqlModel", status.sqlModel(),
                        "lastError", status.lastError() == null ? "" : status.lastError()
                )
        );
    }

    private SystemHealthItem uploadHealth() {
        long start = System.nanoTime();
        try {
            Files.createDirectories(uploadRoot);
            boolean writable = Files.isDirectory(uploadRoot) && Files.isWritable(uploadRoot);
            return new SystemHealthItem(
                    "upload",
                    "上传目录",
                    writable ? "UP" : "DEGRADED",
                    writable ? "上传目录存在且可写" : "上传目录不可写，请检查权限",
                    elapsedMillis(start),
                    Map.of(
                            "path", uploadRoot.toString(),
                            "freeSpaceBytes", uploadRoot.toFile().getFreeSpace()
                    )
            );
        } catch (IOException | RuntimeException ex) {
            return failedHealth("upload", "上传目录", "上传目录检查失败", start, ex);
        }
    }

    private SystemHealthItem flywayHealth() {
        long start = System.nanoTime();
        try {
            Map<String, Object> latest = jdbcTemplate.queryForMap("""
                    select version, description, success, installed_on
                    from flyway_schema_history
                    order by installed_rank desc
                    limit 1
                    """);
            boolean success = Boolean.parseBoolean(String.valueOf(latest.getOrDefault("success", "false")));
            return new SystemHealthItem(
                    "flyway",
                    "Flyway 迁移",
                    success ? "UP" : "DEGRADED",
                    "最新迁移：" + latest.getOrDefault("version", "") + " " + latest.getOrDefault("description", ""),
                    elapsedMillis(start),
                    latest
            );
        } catch (RuntimeException ex) {
            return failedHealth("flyway", "Flyway 迁移", "无法读取迁移历史", start, ex);
        }
    }

    private SystemHealthItem jvmHealth() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long usagePercent = max <= 0 ? 0 : Math.round(used * 100.0 / max);
        return new SystemHealthItem(
                "jvm",
                "JVM 运行时",
                usagePercent < 85 ? "UP" : "DEGRADED",
                "内存占用 " + usagePercent + "%，线程数 " + Thread.activeCount(),
                0,
                Map.of(
                        "usedMemoryBytes", used,
                        "maxMemoryBytes", max,
                        "availableProcessors", runtime.availableProcessors(),
                        "activeThreads", Thread.activeCount()
                )
        );
    }

    private List<SystemMetricItem> jvmMetrics() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        long free = uploadRoot.toFile().getFreeSpace();
        return List.of(
                new SystemMetricItem("jvm.usedMemory", "JVM 已用内存", used / 1024 / 1024, "MB"),
                new SystemMetricItem("jvm.maxMemory", "JVM 最大内存", max / 1024 / 1024, "MB"),
                new SystemMetricItem("jvm.threads", "活动线程", Thread.activeCount(), "个"),
                new SystemMetricItem("disk.uploadFree", "上传盘剩余", free / 1024 / 1024, "MB")
        );
    }

    private SystemHealthItem failedHealth(String key, String name, String detail, long start, Exception ex) {
        return new SystemHealthItem(
                key,
                name,
                "DOWN",
                detail,
                elapsedMillis(start),
                Map.of("error", ex.getClass().getSimpleName() + ": " + ex.getMessage())
        );
    }

    private String overallStatus(List<SystemHealthItem> items) {
        if (items.stream().anyMatch(item -> item.status().equals("DOWN"))) {
            return "DOWN";
        }
        if (items.stream().anyMatch(item -> item.status().equals("DEGRADED"))) {
            return "DEGRADED";
        }
        return "UP";
    }

    private long elapsedMillis(long startNanos) {
        return java.time.Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private Integer queryInteger(String sql, Object... args) {
        List<Integer> values = jdbcTemplate.queryForList(sql, Integer.class, args);
        return values.isEmpty() ? null : values.get(0);
    }

    private LoadTestReportRow readReportRow(Path jsonFile) {
        String jsonName = jsonFile.getFileName().toString();
        String htmlName = jsonName.replaceFirst("\\.json$", ".html");
        Path htmlPath = reportDir.resolve(htmlName);
        try {
            JsonNode root = objectMapper.readTree(jsonFile.toFile());
            JsonNode summary = root.path("summary");
            JsonNode config = root.path("config");
            JsonNode redis = root.path("redis");
            long modified = lastModifiedMillis(jsonFile);
            return new LoadTestReportRow(
                    jsonName,
                    Files.isRegularFile(htmlPath) ? htmlName : null,
                    root.path("startedAt").asText(""),
                    DISPLAY_TIME_FORMATTER.format(Instant.ofEpochMilli(modified)),
                    summary.path("requestCount").asInt(0),
                    summary.path("byStatus").path("SUCCESS").asInt(0),
                    summary.path("byStatus").path("FULL").asInt(0),
                    summary.path("throughput").asDouble(0),
                    summary.path("avgLatency").asDouble(0),
                    summary.path("p95").asDouble(0),
                    config.path("smartMode").asText(""),
                    config.path("concurrency").asInt(0),
                    redis.path("reachable").asBoolean(false)
            );
        } catch (IOException ex) {
            return new LoadTestReportRow(
                    jsonName,
                    Files.isRegularFile(htmlPath) ? htmlName : null,
                    "",
                    DISPLAY_TIME_FORMATTER.format(Instant.ofEpochMilli(lastModifiedMillis(jsonFile))),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "",
                    0,
                    false
            );
        }
    }

    private long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            return 0;
        }
    }

    public record RedisMonitorResponse(
            boolean reachable,
            String ping,
            String pattern,
            long dbSize,
            int displayedKeyCount,
            long elapsedMs,
            List<RedisKeyRow> keys,
            List<RedisStockCheckRow> stockChecks,
            String error
    ) {
    }

    public record RedisKeyRow(String key, long ttlSeconds, String value) {
    }

    public record RedisStockCheckRow(
            long offeringId,
            String key,
            Integer redisRemaining,
            int databaseCapacity,
            int databaseSelected,
            int databaseRemaining,
            boolean oversold
    ) {
    }

    public record OfferingStockRow(
            long offeringId,
            int capacity,
            int selected
    ) {
    }

    public record PrewarmStockResponse(
            int count,
            List<PrewarmStockItem> items
    ) {
    }

    public record PrewarmStockItem(
            long offeringId,
            String key,
            long remaining
    ) {
    }

    public record LoadTestReportRow(
            String jsonName,
            String htmlName,
            String startedAt,
            String modifiedAt,
            int requestCount,
            int successCount,
            int fullCount,
            double throughput,
            double avgLatency,
            double p95,
            String smartMode,
            int concurrency,
            boolean redisReachable
    ) {
    }

    public record SystemHealthResponse(
            Instant checkedAt,
            String overallStatus,
            List<SystemHealthItem> items,
            List<SystemMetricItem> metrics
    ) {
    }

    public record SystemHealthItem(
            String key,
            String name,
            String status,
            String detail,
            long latencyMs,
            Map<String, Object> metadata
    ) {
    }

    public record SystemMetricItem(
            String key,
            String label,
            long value,
            String unit
    ) {
    }
}
