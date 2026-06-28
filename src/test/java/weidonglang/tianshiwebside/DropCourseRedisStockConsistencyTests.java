package weidonglang.tianshiwebside;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import weidonglang.tianshiwebside.audit.AuditLogService;
import weidonglang.tianshiwebside.course.CourseSelectionConsistencyController;
import weidonglang.tianshiwebside.course.CourseSelectionStockService;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DropCourseRedisStockConsistencyTests {
    @Test
    void dropCourseRebuildsRedisStockFromDatabaseAndConsistencyReportPasses() {
        JdbcTemplate jdbcTemplate = jdbc();
        seedOffering(jdbcTemplate, 3);
        jdbcTemplate.update("insert into course_selection (id, student_id, offering_id, selected_at) values (1, 1, 10, ?)", Instant.now());
        jdbcTemplate.update("insert into course_selection (id, student_id, offering_id, selected_at) values (2, 2, 10, ?)", Instant.now());

        RedisFixture redis = redisFixture();
        AuditLogService auditLogService = mock(AuditLogService.class);
        CourseSelectionStockService stockService = new CourseSelectionStockService(jdbcTemplate, redis.template, auditLogService);

        stockService.rebuildFromDatabase(10L, "student-a", "REBUILD_STOCK_AFTER_SELECT");
        assertThat(redis.stock.get()).isEqualTo("1");

        jdbcTemplate.update("delete from course_selection where id = 1");
        var result = stockService.rebuildFromDatabase(10L, "student-a", "REBUILD_STOCK_AFTER_DROP");
        assertThat(result.success()).isTrue();
        assertThat(result.remaining()).isEqualTo(2);
        assertThat(redis.stock.get()).isEqualTo("2");

        CourseSelectionConsistencyController controller =
                new CourseSelectionConsistencyController(jdbcTemplate, redis.template, auditLogService);
        var report = controller.report(10).data();
        assertThat(report.rows()).anySatisfy(row -> {
            assertThat(row.offeringId()).isEqualTo(10L);
            assertThat(row.expectedStock()).isEqualTo(2);
            assertThat(row.redisStock()).isEqualTo(2);
            assertThat(row.consistent()).isTrue();
        });
    }

    @Test
    void redisFailureDoesNotFailDatabaseDropClosureAndWritesDegradedAudit() {
        JdbcTemplate jdbcTemplate = jdbc();
        seedOffering(jdbcTemplate, 1);

        RedisFixture redis = redisFixture();
        doThrow(new IllegalStateException("Redis down"))
                .when(redis.valueOps).set(eq("selection:offering:10:remaining"), anyString(), any(Duration.class));
        AuditLogService auditLogService = mock(AuditLogService.class);
        CourseSelectionStockService stockService = new CourseSelectionStockService(jdbcTemplate, redis.template, auditLogService);

        var result = stockService.rebuildFromDatabase(10L, "student-a", "REBUILD_STOCK_AFTER_DROP");

        assertThat(result.success()).isFalse();
        verify(auditLogService).record(eq("student-a"), eq("REBUILD_STOCK_AFTER_DROP_DEGRADED"),
                eq("COURSE_OFFERING"), eq(10L), contains("DB remains authoritative"), any(), eq(false), contains("Redis down"));
    }

    private JdbcTemplate jdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:dropstock" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("create table course (id bigint primary key, name varchar(120) not null)");
        jdbcTemplate.execute("create table course_offering (id bigint primary key, course_id bigint not null, capacity integer not null)");
        jdbcTemplate.execute("create table course_selection (id bigint primary key, student_id bigint not null, offering_id bigint not null, selected_at timestamp not null)");
        return jdbcTemplate;
    }

    private void seedOffering(JdbcTemplate jdbcTemplate, int capacity) {
        jdbcTemplate.update("insert into course (id, name) values (1, 'Redis 库存闭环课程')");
        jdbcTemplate.update("insert into course_offering (id, course_id, capacity) values (10, 1, ?)", capacity);
    }

    @SuppressWarnings("unchecked")
    private RedisFixture redisFixture() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        AtomicReference<String> stock = new AtomicReference<>();

        when(template.opsForValue()).thenReturn(valueOps);
        doAnswer(invocation -> {
            stock.set(invocation.getArgument(1));
            return null;
        }).when(valueOps).set(eq("selection:offering:10:remaining"), anyString(), any(Duration.class));
        when(valueOps.get("selection:offering:10:remaining")).thenAnswer(invocation -> stock.get());
        when(template.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");
        return new RedisFixture(template, valueOps, stock);
    }

    private record RedisFixture(
            StringRedisTemplate template,
            ValueOperations<String, String> valueOps,
            AtomicReference<String> stock
    ) {
    }
}
