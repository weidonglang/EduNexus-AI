package weidonglang.tianshiwebside.dashboard;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import weidonglang.tianshiwebside.common.api.ApiResponse;
import weidonglang.tianshiwebside.common.cache.QueryCacheService;
import weidonglang.tianshiwebside.academic.TermService;
import weidonglang.tianshiwebside.common.error.BusinessException;
import weidonglang.tianshiwebside.common.error.ErrorCode;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * 首页仪表盘接口。
 *
 * 登录后前端 Dashboard 页面会调用这里，聚合当前学期课程数量、待评价数量、考试安排、
 * 已获学分和近期事件。它的作用是把多个业务模块的摘要信息集中到首页展示。
 */
@RestController
public class DashboardController {
    private final DashboardMapper dashboardMapper;
    private final QueryCacheService queryCacheService;
    private final TermService termService;
    private final JdbcTemplate jdbcTemplate;

    public DashboardController(
            DashboardMapper dashboardMapper,
            QueryCacheService queryCacheService,
            TermService termService,
            JdbcTemplate jdbcTemplate
    ) {
        this.dashboardMapper = dashboardMapper;
        this.queryCacheService = queryCacheService;
        this.termService = termService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询当前登录用户首页概览。
     *
     * Principal 来自 Spring Security，保证只读取当前账号相关的数据，
     * 学生、教师、管理员进入首页时都可以复用这一接口。
     */
    @GetMapping("/api/dashboard/me")
    public ApiResponse<DashboardOverview> overview(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String username = authentication.getName();
        String currentTerm = termService.resolveTerm(null);
        return ApiResponse.success(queryCacheService.get(
                "query:dashboard:" + username + ":" + currentTerm,
                Duration.ofSeconds(20),
                new TypeReference<DashboardOverview>() {
                },
                () -> buildOverview(username, currentTerm)
        ));
    }

    private DashboardOverview buildOverview(String username, String term) {
        List<String> roles = roles(username);
        if (roles.contains("ADMIN")) {
            return adminOverview(term);
        }
        if (roles.contains("TEACHER")) {
            return teacherOverview(username, term);
        }
        if (roles.contains("STUDENT")) {
            return studentOverview(username, term);
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号未分配学生、教师或管理员角色，无法生成首页统计");
    }

    private DashboardOverview studentOverview(String username, String term) {
        int selectedCourses = count("""
                select count(*)
                from course_selection cs
                join student s on s.id = cs.student_id
                join sys_user u on u.id = s.user_id
                join course_offering co on co.id = cs.offering_id
                where u.username = ? and co.term = ?
                """, username, term);
        int exams = count("""
                select count(*)
                from exam_schedule es
                join course_offering co on co.id = es.course_offering_id
                join course_selection cs on cs.offering_id = co.id
                join student s on s.id = cs.student_id
                join sys_user u on u.id = s.user_id
                where u.username = ? and co.term = ?
                """, username, term);
        int unread = unreadNotifications(username);
        int processing = count("""
                select
                  (select count(*) from student_status_change_application a
                   join student s on s.id = a.student_id
                   join sys_user u on u.id = s.user_id
                   where u.username = ? and a.status in ('PENDING','SUBMITTED','PROCESSING'))
                  +
                  (select count(*) from student_registration_application a
                   join student s on s.id = a.student_id
                   join sys_user u on u.id = s.user_id
                   where u.username = ? and a.status in ('PENDING','SUBMITTED','PROCESSING'))
                """, username, username);
        int pendingEvaluation = count("""
                select count(*)
                from course_selection cs
                join student s on s.id = cs.student_id
                join sys_user u on u.id = s.user_id
                join course_offering co on co.id = cs.offering_id
                left join teaching_evaluation te on te.student_id = s.id and te.offering_id = cs.offering_id
                where u.username = ? and co.term = ? and te.id is null
                """, username, term);
        List<DashboardOverview.DashboardCard> cards = List.of(
                card("selectedCourses", "本学期已选课程", selectedCourses, "门", "个人"),
                card("upcomingExams", "待考试数量", exams, "场", "个人"),
                card("unreadNotices", "未读通知", unread, "条", "个人"),
                card("processingApplications", "申请处理中", processing, "项", "个人"),
                card("pendingEvaluations", "待完成教学评价", pendingEvaluation, "项", "个人")
        );
        return new DashboardOverview("STUDENT", term, "个人", cards, selectedCourses, pendingEvaluation, exams,
                dashboardMapper.sumEarnedCredits(username), dashboardMapper.findRecentEvents(username));
    }

    private DashboardOverview teacherOverview(String username, String term) {
        String teacherName = displayName(username);
        int offerings = count("select count(*) from course_offering where teacher_name = ? and term = ?", teacherName, term);
        int pendingGrades = count("""
                select count(*)
                from course_selection cs
                join course_offering co on co.id = cs.offering_id
                join course c on c.id = co.course_id
                left join academic_grade ag on ag.student_id = cs.student_id and ag.course_id = c.id and ag.term = co.term
                where co.teacher_name = ? and co.term = ? and ag.id is null
                """, teacherName, term);
        int exams = count("""
                select count(*)
                from exam_schedule es
                join course_offering co on co.id = es.course_offering_id
                where co.teacher_name = ? and co.term = ?
                """, teacherName, term);
        int evaluations = count("""
                select count(*)
                from teaching_evaluation te
                join course_offering co on co.id = te.offering_id
                where co.teacher_name = ? and co.term = ?
                """, teacherName, term);
        int applications = count("""
                select count(distinct a.id)
                from student_status_change_application a
                join student s on s.id = a.student_id
                left join academic_class ac on ac.class_name = s.class_name
                left join sys_user ht on ht.id = ac.homeroom_teacher_user_id
                left join course_selection cs on cs.student_id = s.id
                left join course_offering co on co.id = cs.offering_id
                where a.status in ('PENDING','SUBMITTED','PROCESSING')
                  and (ht.username = ? or co.teacher_name = ?)
                """, username, teacherName);
        int unread = unreadNotifications(username);
        List<DashboardOverview.DashboardCard> cards = List.of(
                card("teacherOfferings", "本学期教学班", offerings, "个", "本人"),
                card("pendingGrades", "待录入成绩", pendingGrades, "项", "本人"),
                card("teacherExams", "考试安排", exams, "场", "本人"),
                card("receivedEvaluations", "收到评价", evaluations, "条", "本人"),
                card("relatedApplications", "相关学生申请", applications, "项", "本人"),
                card("unreadNotices", "未读通知", unread, "条", "本人")
        );
        return new DashboardOverview("TEACHER", term, "本人", cards, offerings, pendingGrades, exams, 0,
                recentTeacherEvents(teacherName, term));
    }

    private DashboardOverview adminOverview(String term) {
        int offerings = dashboardMapper.countTermOfferings(term);
        int pending = count("""
                select
                  (select count(*) from student_status_change_application where status in ('PENDING','SUBMITTED','PROCESSING'))
                  +
                  (select count(*) from student_registration_application where status in ('PENDING','SUBMITTED','PROCESSING'))
                """);
        int exams = count("""
                select count(*)
                from exam_schedule es
                join course_offering co on co.id = es.course_offering_id
                where co.term = ?
                """, term);
        int roles = count("select count(*) from sys_role");
        int failedTasks = count("select count(*) from batch_task where status in ('FAILED','PARTIAL_SUCCESS')");
        List<DashboardOverview.DashboardCard> cards = List.of(
                card("globalOfferings", "本学期教学班", offerings, "个", "全局"),
                card("pendingMaintenance", "待维护事项", pending, "项", "全局"),
                card("globalExams", "考试安排", exams, "场", "全局"),
                card("roleGroups", "管理权限组", roles, "组", "系统级"),
                card("abnormalBatchTasks", "批量任务异常", failedTasks, "项", "系统级")
        );
        return new DashboardOverview("ADMIN", term, "全局", cards, offerings, pending, exams, roles,
                recentAdminEvents());
    }

    private List<String> roles(String username) {
        return jdbcTemplate.queryForList("""
                        select r.code
                        from sys_user u
                        join sys_user_role ur on ur.user_id = u.id
                        join sys_role r on r.id = ur.role_id
                        where u.username = ?
                        """,
                String.class,
                username
        ).stream().map(role -> role.toUpperCase(Locale.ROOT)).toList();
    }

    private String displayName(String username) {
        String value = jdbcTemplate.queryForObject("select display_name from sys_user where username = ?", String.class, username);
        return value == null ? username : value;
    }

    private int unreadNotifications(String username) {
        return count("""
                select count(*)
                from user_notification n
                join sys_user u on u.id = n.user_id
                where u.username = ? and n.read_flag = false
                """, username);
    }

    private List<DashboardEventRow> recentTeacherEvents(String teacherName, String term) {
        return jdbcTemplate.query("""
                        select '考试' as type, concat(c.name, '考试安排') as title, es.exam_time as event_time
                        from exam_schedule es
                        join course_offering co on co.id = es.course_offering_id
                        join course c on c.id = co.course_id
                        where co.teacher_name = ? and co.term = ?
                        order by es.exam_time asc
                        limit 5
                        """,
                (rs, rowNum) -> new DashboardEventRow(
                        rs.getString("type"),
                        rs.getString("title"),
                        rs.getObject("event_time", java.time.LocalDateTime.class)
                ),
                teacherName,
                term
        );
    }

    private List<DashboardEventRow> recentAdminEvents() {
        return jdbcTemplate.query("""
                        select '审计' as type, concat(action, ' / ', target_type) as title, created_at as event_time
                        from operation_audit_log
                        order by created_at desc
                        limit 5
                        """,
                (rs, rowNum) -> new DashboardEventRow(
                        rs.getString("type"),
                        rs.getString("title"),
                        rs.getObject("event_time", java.time.LocalDateTime.class)
                )
        );
    }

    private DashboardOverview.DashboardCard card(String key, String label, int value, String suffix, String scope) {
        return new DashboardOverview.DashboardCard(key, label, value, suffix, scope);
    }

    private int count(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0 : value.intValue();
    }
}
