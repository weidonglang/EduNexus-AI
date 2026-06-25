package weidonglang.tianshiwebside.course.grab;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import weidonglang.tianshiwebside.common.error.BusinessException;
import weidonglang.tianshiwebside.common.error.ErrorCode;
import weidonglang.tianshiwebside.course.SelectionWindowStatus;
import weidonglang.tianshiwebside.course.mapper.CourseOfferingDetailRow;
import weidonglang.tianshiwebside.course.mapper.CourseSelectionWriteMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@ConditionalOnMissingBean(RemoteCourseGrabClient.class)
public class LocalCourseGrabService implements CourseGrabPort {
    private static final Duration LOCK_TTL = Duration.ofSeconds(8);
    private static final Duration REQUEST_TTL = Duration.ofMinutes(10);
    private static final Duration STOCK_TTL = Duration.ofMinutes(30);
    private static final Duration OFFERING_CACHE_TTL = Duration.ofSeconds(30);
    private static final Duration STUDENT_CACHE_TTL = Duration.ofMinutes(10);
    private static final DefaultRedisScript<String> GRAB_SCRIPT = new DefaultRedisScript<>("""
            local requestKey = KEYS[1]
            local lockKey = KEYS[2]
            local stockKey = KEYS[3]
            local lockTtl = tonumber(ARGV[1])
            local requestTtl = tonumber(ARGV[2])
            local stockTtl = tonumber(ARGV[3])
            local initialRemaining = tonumber(ARGV[4])

            if redis.call('EXISTS', stockKey) == 0 then
                if initialRemaining < 0 then
                    return 'NO_STOCK'
                end
                redis.call('SET', stockKey, initialRemaining, 'EX', stockTtl, 'NX')
            end

            local currentRemaining = tonumber(redis.call('GET', stockKey) or '0')
            if currentRemaining <= 0 then
                return 'FULL_FAST'
            end

            local requestValue = redis.call('GET', requestKey)
            if requestValue and requestValue ~= 'PROCESSING' then
                return 'DUPLICATE_DONE|' .. requestValue
            end
            if requestValue == 'PROCESSING' then
                return 'DUPLICATE_PROCESSING'
            end

            redis.call('SET', requestKey, 'PROCESSING', 'EX', requestTtl, 'NX')
            if redis.call('SET', lockKey, '1', 'EX', lockTtl, 'NX') == false then
                redis.call('DEL', requestKey)
                return 'BUSY'
            end

            -- stockKey 对应 selection:offering:{offeringId}:remaining。
            -- 抢课请求先在 Redis 中扣减剩余名额，避免大量并发请求直接竞争 MySQL。
            local remaining = redis.call('DECR', stockKey)
            if remaining < 0 then
                -- 扣减后小于 0 表示课程已满，立即把刚才扣掉的库存补回去并返回 FULL。
                redis.call('INCR', stockKey)
                redis.call('SET', requestKey, 'FULL||||', 'EX', requestTtl)
                return 'FULL'
            end

            return 'RESERVED|' .. remaining
            """, String.class);
    // 上面的 Lua 脚本把“幂等判断、短锁、库存扣减”放在 Redis 内一次执行，
    // 重点库存 key 是 selection:offering:{offeringId}:remaining。
    private final CourseSelectionWriteMapper selectionWriteMapper;
    private final StringRedisTemplate redisTemplate;
    private final Map<String, CourseGrabResult> localRequestResults = new ConcurrentHashMap<>();
    private final Map<Long, CacheEntry<CourseOfferingDetailRow>> offeringCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<Long>> studentIdCache = new ConcurrentHashMap<>();
    private final Set<String> localProcessingRequests = ConcurrentHashMap.newKeySet();
    private final Set<Long> initializedStockKeys = ConcurrentHashMap.newKeySet();
    private final AtomicLong redisRetryAfterMillis = new AtomicLong(0);
    private volatile boolean redisEnabled = true;

    public LocalCourseGrabService(
            CourseSelectionWriteMapper selectionWriteMapper,
            StringRedisTemplate redisTemplate
    ) {
        this.selectionWriteMapper = selectionWriteMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional
    /**
     * 功能：实现学生抢课核心流程。
     * 说明：依次完成 requestId 幂等处理、教学班信息缓存、选课时间窗口校验、
     * Redis 抢课路径选择和 Redis 异常时的数据库兜底，最终返回抢课成功或失败原因。
     */
    public CourseGrabResult grab(CourseGrabCommand command) {
        String requestId = normalizeRequestId(command.requestId());
        String requestKey = requestKey(requestId);
        CourseGrabResult cached = localRequestResults.get(requestKey);
        if (cached != null) {
            return cached;
        }

        String lockKey = lockKey(command.username(), command.offeringId());

        try {
            CourseOfferingDetailRow offering = cachedOfferingDetail(command.offeringId());
            if (offering == null) {
                throw failure(CourseGrabFailureReason.OFFERING_NOT_FOUND, "Course offering not found");
            }

            Instant now = Instant.now();
            SelectionWindowStatus windowStatus = windowStatus(offering, now);
            if (windowStatus == SelectionWindowStatus.NOT_STARTED) {
                throw failure(CourseGrabFailureReason.NOT_STARTED, "Course selection has not started");
            }
            if (windowStatus == SelectionWindowStatus.ENDED) {
                throw failure(CourseGrabFailureReason.ENDED, "Course selection has ended");
            }

            if (redisUsable()) {
                return grabWithRedis(command, requestKey, lockKey, offering, now);
            }

            return grabWithDatabaseFallback(command, requestKey, offering, now);
        } catch (RuntimeException ex) {
            if (!(ex instanceof BusinessException)) {
                clearRequest(requestKey);
            }
            throw ex;
        }
    }

    /**
     * 功能：使用 Redis 执行高并发抢课。
     * 说明：先通过 Redis Lua 脚本完成幂等、短锁和库存扣减，Redis 预留成功后再写入数据库。
     * 如果数据库写入失败，会回滚 Redis 库存，保证缓存库存和数据库选课记录尽量一致。
     */
    private CourseGrabResult grabWithRedis(
            CourseGrabCommand command,
            String requestKey,
            String lockKey,
            CourseOfferingDetailRow offering,
            Instant selectedAt
    ) {
        if (!reserveStockWithRedisScript(requestKey, lockKey, offering)) {
            return grabWithDatabaseFallback(command, requestKey, offering, selectedAt);
        }

        var insertCommand = new CourseSelectionWriteMapper.InsertCourseSelectionByUsernameCommand(
                command.username(),
                offering.offeringId(),
                selectedAt
        );
        try {
            Long studentId = cachedStudentId(command.username());
            if (studentId == null) {
                rollbackStock(offering, true);
                clearRequest(requestKey);
                throw failure(CourseGrabFailureReason.STUDENT_NOT_FOUND, "Student profile not found");
            }
            ensureNoScheduleConflict(studentId, offering.offeringId());
            int inserted = selectionWriteMapper.insertSelectionByUsername(insertCommand);
            if (inserted == 0) {
                rollbackStock(offering, true);
                clearRequest(requestKey);
                throw failure(CourseGrabFailureReason.STUDENT_NOT_FOUND, "Student profile not found");
            }
        } catch (BusinessException ex) {
            rollbackStock(offering, true);
            clearRequest(requestKey);
            throw ex;
        } catch (DataIntegrityViolationException ex) {
            rollbackStock(offering, true);
            throw failure(CourseGrabFailureReason.ALREADY_SELECTED, "Course already selected");
        }

        CourseGrabResult result = toResult(insertCommand.getSelectionId(), offering, selectedAt, "SUCCESS", null, "Course selected");
        cacheRequestResult(requestKey, result);
        return result;
    }

    /**
     * 功能：Redis 不可用时使用数据库兜底抢课。
     * 说明：当 Redis 连接异常、被手动关闭或触发熔断时，系统仍然通过数据库校验重复选课、
     * 统计已选人数并写入选课记录，保证功能可用，但并发性能会低于 Redis 路径。
     */
    private CourseGrabResult grabWithDatabaseFallback(
            CourseGrabCommand command,
            String requestKey,
            CourseOfferingDetailRow offering,
            Instant selectedAt
    ) {
        Long studentId = cachedStudentId(command.username());
        if (studentId == null) {
            throw failure(CourseGrabFailureReason.STUDENT_NOT_FOUND, "Student profile not found");
        }

        ensureNoScheduleConflict(studentId, offering.offeringId());

        if (selectionWriteMapper.countStudentOfferingSelection(studentId, offering.offeringId()) > 0) {
            throw failure(CourseGrabFailureReason.ALREADY_SELECTED, "Course already selected");
        }

        if (!markRequestProcessing(requestKey)) {
            throw failure(CourseGrabFailureReason.DUPLICATE_REQUEST, "Duplicate selection request");
        }

        Long lockedOfferingId = selectionWriteMapper.lockOfferingForUpdate(offering.offeringId());
        if (lockedOfferingId == null) {
            clearRequest(requestKey);
            throw failure(CourseGrabFailureReason.OFFERING_NOT_FOUND, "Course offering not found");
        }

        long selectedCount = selectionWriteMapper.countOfferingSelections(offering.offeringId());
        if (selectedCount >= offering.capacity()) {
            clearRequest(requestKey);
            throw failure(CourseGrabFailureReason.FULL, "Course capacity is full");
        }

        var insertCommand = new CourseSelectionWriteMapper.InsertCourseSelectionCommand(
                studentId,
                offering.offeringId(),
                selectedAt
        );
        try {
            selectionWriteMapper.insertSelection(insertCommand);
        } catch (DataIntegrityViolationException ex) {
            clearRequest(requestKey);
            throw failure(CourseGrabFailureReason.ALREADY_SELECTED, "Course already selected");
        }

        CourseGrabResult result = toResult(insertCommand.getSelectionId(), offering, selectedAt, "SUCCESS", null, "Course selected");
        cacheRequestResult(requestKey, result);
        return result;
    }

    private void ensureNoScheduleConflict(Long studentId, Long offeringId) {
        if (selectionWriteMapper.countStudentScheduleConflicts(studentId, offeringId) > 0) {
            throw new BusinessException(ErrorCode.COURSE_TIME_CONFLICT, "课程时间冲突");
        }
    }

    /**
     * 功能：缓存教学班详情。
     * 说明：教学班容量、时间窗口、教师和教室在短时间内变化较少，
     * 使用本地短缓存可以减少高并发抢课时对 course_offering 的重复查询。
     */
    private CourseOfferingDetailRow cachedOfferingDetail(Long offeringId) {
        CacheEntry<CourseOfferingDetailRow> cached = offeringCache.get(offeringId);
        if (cached != null && cached.fresh()) {
            return cached.value();
        }
        CourseOfferingDetailRow row = selectionWriteMapper.findOfferingDetail(offeringId);
        if (row != null) {
            offeringCache.put(offeringId, CacheEntry.of(row, OFFERING_CACHE_TTL));
        }
        return row;
    }

    /**
     * 功能：缓存学生 ID。
     * 说明：数据库选课记录使用 student_id，抢课请求使用登录账号，
     * 本地缓存可减少压测和高并发场景下重复查询 student 表。
     */
    private Long cachedStudentId(String username) {
        String key = Objects.toString(username, "");
        CacheEntry<Long> cached = studentIdCache.get(key);
        if (cached != null && cached.fresh()) {
            return cached.value();
        }
        Long studentId = selectionWriteMapper.findStudentIdByUsername(username);
        if (studentId != null) {
            studentIdCache.put(key, CacheEntry.of(studentId, STUDENT_CACHE_TTL));
        }
        return studentId;
    }

    private CourseGrabResult toResult(
            Long selectionId,
            CourseOfferingDetailRow offering,
            Instant selectedAt,
            String status,
            CourseGrabFailureReason failureReason,
            String message
    ) {
        return new CourseGrabResult(
                selectionId,
                offering.offeringId(),
                offering.courseCode(),
                offering.courseName(),
                offering.credit(),
                offering.teacherName(),
                offering.scheduleText(),
                offering.classroom(),
                selectedAt,
                status,
                failureReason,
                message
        );
    }

    /**
     * 功能：调用 Redis Lua 脚本预留库存。
     * 说明：Lua 脚本在 Redis 内原子执行，统一完成 requestId 幂等判断、学生短锁、
     * selection:offering:{offeringId}:remaining 库存扣减，避免并发请求出现库存竞争问题。
     */
    private boolean reserveStockWithRedisScript(String requestKey, String lockKey, CourseOfferingDetailRow offering) {
        if (!redisUsable()) {
            return false;
        }
        String stockKey = stockKey(offering.offeringId());
        try {
            long initialRemaining = initialRemainingForRedisStock(stockKey, offering);
            String response = redisTemplate.execute(
                    GRAB_SCRIPT,
                    java.util.List.of(requestKey, lockKey, stockKey),
                    Long.toString(LOCK_TTL.toSeconds()),
                    Long.toString(REQUEST_TTL.toSeconds()),
                    Long.toString(STOCK_TTL.toSeconds()),
                    Long.toString(initialRemaining)
            );
            if (response == null || response.startsWith("NO_STOCK")) {
                return false;
            }
            if (response.startsWith("RESERVED")) {
                initializedStockKeys.add(offering.offeringId());
                return true;
            }
            if (response.startsWith("FULL")) {
                throw failure(CourseGrabFailureReason.FULL, "Course capacity is full");
            }
            if (response.startsWith("BUSY")) {
                throw failure(CourseGrabFailureReason.BUSY, "Selection request is already processing");
            }
            if (response.startsWith("DUPLICATE_DONE")) {
                CourseGrabResult cached = parseCachedRequest(response.substring("DUPLICATE_DONE|".length()));
                if (cached != null) {
                    throw failure(CourseGrabFailureReason.DUPLICATE_REQUEST, "Duplicate request reused");
                }
                throw failure(CourseGrabFailureReason.DUPLICATE_REQUEST, "Duplicate selection request");
            }
            if (response.startsWith("DUPLICATE_PROCESSING")) {
                throw failure(CourseGrabFailureReason.DUPLICATE_REQUEST, "Duplicate selection request is processing");
            }
            return false;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            markRedisUnavailable(ex);
            return false;
        }
    }

    /**
     * 功能：计算 Redis 库存初始化值。
     * 说明：当库存 key 尚未预热时，根据数据库容量和已选人数计算剩余名额；
     * 如果 key 已存在则返回 -1，表示 Lua 脚本不需要重新覆盖 Redis 库存。
     */
    private long initialRemainingForRedisStock(String stockKey, CourseOfferingDetailRow offering) {
        if (initializedStockKeys.contains(offering.offeringId())) {
            return -1;
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(stockKey))) {
            initializedStockKeys.add(offering.offeringId());
            return -1;
        }
        long selected = selectionWriteMapper.countOfferingSelections(offering.offeringId());
        return Math.max(0, offering.capacity() - selected);
    }

    /**
     * 功能：预热指定教学班 Redis 库存。
     * 说明：管理端或压测脚本调用本方法，把数据库中的剩余名额提前写入
     * selection:offering:{offeringId}:remaining，减少压测开始时的缓存 miss。
     */
    public long prewarmOfferingStock(Long offeringId) {
        CourseOfferingDetailRow offering = cachedOfferingDetail(offeringId);
        if (offering == null) {
            throw failure(CourseGrabFailureReason.OFFERING_NOT_FOUND, "Course offering not found");
        }

        long selected = selectionWriteMapper.countOfferingSelections(offering.offeringId());
        long remaining = Math.max(0, offering.capacity() - selected);
        try {
            // 预热库存：根据数据库容量和已选人数计算剩余名额，写入
            // selection:offering:{offeringId}:remaining，供后续抢课请求直接扣减。
            redisTemplate.opsForValue().set(stockKey(offering.offeringId()), Long.toString(remaining), STOCK_TTL);
            initializedStockKeys.add(offering.offeringId());
        } catch (RuntimeException ex) {
            markRedisUnavailable(ex);
        }
        return remaining;
    }

    /**
     * 功能：删除指定教学班 Redis 库存。
     * 说明：管理员修改教学班容量或清理演示数据后，需要删除旧库存 key，
     * 下次预热或抢课时再按数据库最新数据重新生成。
     */
    public void evictOfferingStock(Long offeringId) {
        initializedStockKeys.remove(offeringId);
        try {
            redisTemplate.delete(stockKey(offeringId));
        } catch (RuntimeException ex) {
            markRedisUnavailable(ex);
        }
    }

    /**
     * 功能：回滚 Redis 库存。
     * 说明：Redis 扣减成功但数据库插入失败时，把库存加回去，
     * 避免 Redis 剩余名额和数据库实际选课人数不一致。
     */
    private void rollbackStock(CourseOfferingDetailRow offering, boolean stockReserved) {
        if (!stockReserved) {
            return;
        }
        try {
            // Redis 已经扣过库存，但数据库最终写入失败时，需要补回 1 个名额。
            // 这样可以避免 Redis 剩余库存比数据库实际剩余名额更少。
            redisTemplate.opsForValue().increment(stockKey(offering.offeringId()));
        } catch (RuntimeException ex) {
            markRedisUnavailable(ex);
            // Database is still the final source of truth.
        }
    }

    private String normalizeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return "server-" + UUID.randomUUID();
        }
        return requestId.trim();
    }

    /**
     * 功能：标记抢课请求正在处理。
     * 说明：用于数据库兜底路径的 requestId 幂等控制，防止同一 requestId 被重复执行。
     */
    private boolean markRequestProcessing(String requestKey) {
        if (!redisUsable()) {
            return localProcessingRequests.add(requestKey);
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(requestKey, "PROCESSING", REQUEST_TTL));
        } catch (RuntimeException ex) {
            markRedisUnavailable(ex);
            return localProcessingRequests.add(requestKey);
        }
    }

    /**
     * 功能：读取已处理过的抢课请求结果。
     * 说明：如果同一个 requestId 再次到达，优先返回或拦截已有处理结果，
     * 避免重复写入 course_selection。
     */
    private CourseGrabResult readCachedRequest(String requestKey) {
        CourseGrabResult local = localRequestResults.get(requestKey);
        if (local != null) {
            return local;
        }
        if (!redisUsable()) {
            return null;
        }
        try {
            String value = redisTemplate.opsForValue().get(requestKey);
            if (value == null || "PROCESSING".equals(value)) {
                return null;
            }
            return parseCachedRequest(value);
        } catch (RuntimeException ex) {
            markRedisUnavailable(ex);
            return null;
        }
    }

    private CourseGrabResult parseCachedRequest(String value) {
        String[] parts = value.split("\\|", -1);
        if (parts.length < 4 || !"SUCCESS".equals(parts[0])) {
            return null;
        }
        return new CourseGrabResult(
                Long.valueOf(parts[1]),
                Long.valueOf(parts[2]),
                parts[3],
                parts.length > 4 ? parts[4] : "",
                null,
                "",
                "",
                "",
                Instant.EPOCH,
                "SUCCESS",
                null,
                "Duplicate request reused"
        );
    }

    /**
     * 功能：缓存抢课请求最终结果。
     * 说明：抢课成功后把结果写入本地缓存和 Redis 幂等 key，后续相同 requestId
     * 可以识别为重复请求。
     */
    private void cacheRequestResult(String requestKey, CourseGrabResult result) {
        localProcessingRequests.remove(requestKey);
        localRequestResults.put(requestKey, result);
        if (!redisUsable()) {
            return;
        }
        try {
            String value = String.join("|",
                    result.status(),
                    Objects.toString(result.selectionId(), ""),
                    Objects.toString(result.offeringId(), ""),
                    Objects.toString(result.courseCode(), ""),
                    Objects.toString(result.courseName(), "")
            );
            redisTemplate.opsForValue().set(requestKey, value, REQUEST_TTL);
        } catch (RuntimeException ex) {
            markRedisUnavailable(ex);
            // Redis idempotency is a high-concurrency guardrail, not the database source of truth.
        }
    }

    private void clearRequest(String requestKey) {
        localProcessingRequests.remove(requestKey);
        localRequestResults.remove(requestKey);
        if (!redisUsable()) {
            return;
        }
        try {
            redisTemplate.delete(requestKey);
        } catch (RuntimeException ex) {
            markRedisUnavailable(ex);
            // No-op in local fallback mode.
        }
    }

    private BusinessException failure(CourseGrabFailureReason reason, String message) {
        ErrorCode code = switch (reason) {
            case STUDENT_NOT_FOUND, OFFERING_NOT_FOUND -> ErrorCode.NOT_FOUND;
            case DUPLICATE_REQUEST, BUSY, NOT_STARTED, ENDED, ALREADY_SELECTED, FULL -> ErrorCode.CONFLICT;
        };
        return new BusinessException(code, reason.name() + ": " + message);
    }

    private boolean tryLock(String key) {
        if (!redisUsable()) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_TTL));
        } catch (RuntimeException ex) {
            markRedisUnavailable(ex);
            return true;
        }
    }

    private void unlock(String key) {
        if (!redisUsable()) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException ex) {
            markRedisUnavailable(ex);
            // Redis is optional in local development.
        }
    }

    /**
     * 功能：判断 Redis 当前是否可用。
     * 说明：同时考虑手动开关和异常熔断时间，避免 Redis 故障时每个请求都等待超时。
     */
    private boolean redisUsable() {
        return redisEnabled && System.currentTimeMillis() >= redisRetryAfterMillis.get();
    }

    /**
     * 功能：记录 Redis 暂时不可用。
     * 说明：Redis 访问异常后短时间熔断，后续请求直接走数据库兜底，
     * 提升 Redis 故障场景下的系统稳定性。
     */
    private void markRedisUnavailable(RuntimeException ex) {
        redisRetryAfterMillis.set(System.currentTimeMillis() + Duration.ofSeconds(10).toMillis());
    }

    public void setRedisEnabled(boolean redisEnabled) {
        this.redisEnabled = redisEnabled;
        if (redisEnabled) {
            redisRetryAfterMillis.set(0);
        }
        if (!redisEnabled) {
            initializedStockKeys.clear();
        }
    }

    public boolean isRedisEnabled() {
        return redisEnabled;
    }

    private SelectionWindowStatus windowStatus(CourseOfferingDetailRow offering, Instant now) {
        if (now.isBefore(offering.selectionStartAt())) {
            return SelectionWindowStatus.NOT_STARTED;
        }
        if (now.isAfter(offering.selectionEndAt())) {
            return SelectionWindowStatus.ENDED;
        }
        return SelectionWindowStatus.OPEN;
    }

    private String lockKey(String username, Long offeringId) {
        // 抢课短锁 key，用于限制同一学生短时间内重复抢同一教学班。
        return "selection:grab:lock:" + offeringId + ":" + Objects.toString(username, "");
    }

    // 教学班库存 key。示例：selection:offering:1:remaining 表示 1 号教学班剩余名额。
    private String stockKey(Long offeringId) {
        return "selection:offering:" + offeringId + ":remaining";
    }

    private String requestKey(String requestId) {
        // 抢课请求幂等 key，用于防止同一个 requestId 被重复处理。
        return "selection:request:" + requestId;
    }

    private record CacheEntry<T>(T value, long expiresAtMillis) {
        static <T> CacheEntry<T> of(T value, Duration ttl) {
            return new CacheEntry<>(value, System.currentTimeMillis() + ttl.toMillis());
        }

        boolean fresh() {
            return System.currentTimeMillis() < expiresAtMillis;
        }
    }
}
