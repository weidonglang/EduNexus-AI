package weidonglang.tianshiwebside.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import weidonglang.tianshiwebside.academic.AcademicGrade;
import weidonglang.tianshiwebside.academic.AcademicGradeRepository;
import weidonglang.tianshiwebside.academic.Classroom;
import weidonglang.tianshiwebside.academic.ClassroomRepository;
import weidonglang.tianshiwebside.academic.ExamSchedule;
import weidonglang.tianshiwebside.academic.ExamScheduleRepository;
import weidonglang.tianshiwebside.course.Course;
import weidonglang.tianshiwebside.course.CourseOffering;
import weidonglang.tianshiwebside.course.CourseOfferingRepository;
import weidonglang.tianshiwebside.course.CourseRepository;
import weidonglang.tianshiwebside.course.CourseSelection;
import weidonglang.tianshiwebside.course.CourseSelectionRepository;
import weidonglang.tianshiwebside.permission.SysMenu;
import weidonglang.tianshiwebside.permission.SysMenuRepository;
import weidonglang.tianshiwebside.student.Student;
import weidonglang.tianshiwebside.student.StudentRepository;
import weidonglang.tianshiwebside.user.SysRole;
import weidonglang.tianshiwebside.user.SysRoleRepository;
import weidonglang.tianshiwebside.user.SysUser;
import weidonglang.tianshiwebside.user.SysUserRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 演示数据初始化配置。
 *
 * dev/demo 环境启动时会自动补齐学院、专业、班级、学生、教师、管理员、课程、
 * 教学班、成绩、考试和菜单权限等基础数据。这样别人拿到项目后，只要数据库能连通，
 * 就可以直接使用 admin001、teacher001、student001 等账号演示系统功能。
 */
@Configuration
@Profile({"dev", "demo"})
public class DataInitializer {
    @Bean
    CommandLineRunner seedDevData(DevDataSeeder seeder) {
        return args -> seeder.seed();
    }

    @Configuration
    static class DevDataSeeder {
        private final SysRoleRepository roleRepository;
        private final SysUserRepository userRepository;
        private final SysMenuRepository menuRepository;
        private final StudentRepository studentRepository;
        private final CourseRepository courseRepository;
        private final CourseOfferingRepository offeringRepository;
        private final CourseSelectionRepository selectionRepository;
        private final AcademicGradeRepository gradeRepository;
        private final ExamScheduleRepository examScheduleRepository;
        private final ClassroomRepository classroomRepository;
        private final JdbcTemplate jdbcTemplate;
        private final PasswordEncoder passwordEncoder;

        DevDataSeeder(
                SysRoleRepository roleRepository,
                SysUserRepository userRepository,
                SysMenuRepository menuRepository,
                StudentRepository studentRepository,
                CourseRepository courseRepository,
                CourseOfferingRepository offeringRepository,
                CourseSelectionRepository selectionRepository,
                AcademicGradeRepository gradeRepository,
                ExamScheduleRepository examScheduleRepository,
                ClassroomRepository classroomRepository,
                JdbcTemplate jdbcTemplate,
                PasswordEncoder passwordEncoder
        ) {
            this.roleRepository = roleRepository;
            this.userRepository = userRepository;
            this.menuRepository = menuRepository;
            this.studentRepository = studentRepository;
            this.courseRepository = courseRepository;
            this.offeringRepository = offeringRepository;
            this.selectionRepository = selectionRepository;
            this.gradeRepository = gradeRepository;
            this.examScheduleRepository = examScheduleRepository;
            this.classroomRepository = classroomRepository;
            this.jdbcTemplate = jdbcTemplate;
            this.passwordEncoder = passwordEncoder;
        }

        @Transactional
        void seed() {
            SysRole studentRole = roleRepository.findByCode("STUDENT")
                    .orElseGet(() -> roleRepository.save(new SysRole("STUDENT", "\u5b66\u751f")));
            SysRole adminRole = roleRepository.findByCode("ADMIN")
                    .orElseGet(() -> roleRepository.save(new SysRole("ADMIN", "\u7ba1\u7406\u5458")));
            SysRole teacherRole = roleRepository.findByCode("TEACHER")
                    .orElseGet(() -> roleRepository.save(new SysRole("TEACHER", "\u6559\u5e08")));

            SysUser user = userRepository.findByUsername("23111141")
                    .orElseGet(() -> new SysUser("23111141", passwordEncoder.encode("123456"), "\u5b66\u751f\u7528\u6237"));
            user.addRole(studentRole);
            user.addRole(adminRole);
            user.addRole(teacherRole);
            if (user.getId() == null) {
                userRepository.save(user);
            }

            if (!studentRepository.existsByStudentNo("23111141")) {
                if (user.getId() == null) {
                    user = userRepository.findByUsername("23111141")
                            .orElseThrow(() -> new IllegalStateException("Seed user missing"));
                }
                Student student = new Student(
                        user,
                        "23111141",
                        "\u4fe1\u606f\u5de5\u7a0b\u5b66\u9662",
                        "\u8f6f\u4ef6\u5de5\u7a0b",
                        "\u8f6f\u4ef6\u5de5\u7a0b 23-1",
                        "2023",
                        "\u5728\u7c4d"
                );
                student.updateContact("13800000000", "student@example.com", "\u5929\u6d25\u5e02\u6b66\u6e05\u533a");
                studentRepository.save(student);
            }

            SysUser teacherUser = userRepository.findByUsername("t001")
                    .orElseGet(() -> new SysUser("t001", passwordEncoder.encode("123456"), "\u5f20\u8001\u5e08"));
            teacherUser.addRole(teacherRole);
            if (teacherUser.getId() == null) {
                userRepository.save(teacherUser);
            }

            seedMenu("dashboard", "\u9996\u9875", "/dashboard", "LayoutDashboard", null, 10);
            seedMenu("ai-assistant", "\u667a\u80fd\u6559\u52a1\u52a9\u624b", "/ai/assistant", "Bot", null, 15);
            seedMenu("ai-chat", "AI \u804a\u5929", "/ai/chat", "MessagesSquare", null, 16);
            seedMenu("student", "\u5b66\u751f\u4fe1\u606f", "/student", "UserRound", null, 20);
            seedMenu("student-profile", "\u4e2a\u4eba\u4fe1\u606f", "/student/profile", "IdCard", "student", 21);
            seedMenu("student-class", "我的班级", "/student/class", "UsersRound", "student", 22);
            seedMenu("student-status-change", "\u5b66\u7c4d\u5f02\u52a8\u7533\u8bf7", "/student/status-change", "FilePenLine", "student", 22);
            seedMenu("registration", "\u62a5\u540d\u7533\u8bf7", "/registration", "FilePenLine", null, 30);
            seedMenu("registration-minor", "\u5fae\u4e13\u4e1a\u62a5\u540d", "/registration/minor", "BadgePlus", "registration", 31);
            seedMenu("registration-retake", "\u91cd\u4fee\u62a5\u540d", "/registration/retake", "RefreshCw", "registration", 32);
            seedMenu("registration-credit-internal", "\u6821\u5185\u5b66\u5206\u8282\u70b9\u66ff\u4ee3\u7533\u8bf7", "/registration/credit-internal", "ArrowLeftRight", "registration", 33);
            seedMenu("registration-credit-external", "\u6821\u5916\u8bfe\u7a0b\u5b66\u5206\u8282\u70b9\u66ff\u4ee3\u7533\u8bf7", "/registration/credit-external", "ArrowLeftRight", "registration", 34);
            seedMenu("registration-score-bonus", "\u6210\u7ee9\u52a0\u5206\u7533\u8bf7", "/registration/score-bonus", "CirclePlus", "registration", 35);
            seedMenu("registration-stream-confirm", "\u5206\u6d41\u4e13\u4e1a\u786e\u8ba4", "/registration/stream-confirm", "CheckCheck", "registration", 36);
            seedMenu("registration-direction-confirm", "\u4e13\u4e1a\u65b9\u5411\u786e\u8ba4", "/registration/direction-confirm", "CheckCheck", "registration", 37);
            seedMenu("course", "\u9009\u8bfe\u8bfe\u8868", "/course", "CalendarDays", null, 40);
            seedMenu("course-selection", "\u81ea\u4e3b\u9009\u8bfe", "/course/selection", "ListChecks", "course", 41);
            seedMenu("schedule-personal", "\u4e2a\u4eba\u8bfe\u8868", "/schedule/personal", "Calendar", "course", 42);
            seedMenu("classroom-free", "\u7a7a\u95f2\u6559\u5ba4", "/classroom/free", "School", "course", 43);
            seedMenu("info-query", "\u4fe1\u606f\u67e5\u8be2", "/information", "Search", null, 45);
            seedMenu("info-warning", "\u5b66\u7c4d\u9884\u8b66\u67e5\u8be2", "/information/academic-warning", "TriangleAlert", "info-query", 47);
            seedMenu("info-graduation-audit", "\u6bd5\u4e1a\u5ba1\u6838\u7ed3\u679c\u6838\u67e5", "/information/graduation-audit", "BadgeCheck", "info-query", 48);
            seedMenu("info-class-schedule", "\u73ed\u7ea7\u8bfe\u8868\u67e5\u8be2", "/information/class-schedule", "CalendarRange", "info-query", 49);
            seedMenu("info-roster", "\u9009\u8bfe\u540d\u5355\u67e5\u8be2", "/information/course-roster", "ListChecks", "info-query", 52);
            seedMenu("info-academic-progress", "\u5b66\u751f\u5b66\u4e1a\u60c5\u51b5\u67e5\u8be2", "/information/academic-progress", "GraduationCap", "info-query", 55);
            seedMenu("ai-academic-profile", "\u5b66\u4e1a\u753b\u50cf", "/ai/academic-profile", "Radar", "info-query", 55);
            seedMenu("info-teaching-plan", "\u6559\u5b66\u6267\u884c\u8ba1\u5212\u67e5\u770b", "/information/teaching-plan", "BookOpenText", "info-query", 56);
            seedMenu("grade", "\u6210\u7ee9\u8003\u8bd5", "/grade", "ClipboardList", null, 58);
            seedMenu("grade-query", "\u6210\u7ee9\u67e5\u8be2", "/grade/query", "ChartNoAxesColumn", "grade", 59);
            seedMenu("exam-query", "\u8003\u8bd5\u5b89\u6392", "/exam/query", "NotebookTabs", "grade", 60);
            seedMenu("evaluation", "\u6559\u5b66\u8bc4\u4ef7", "/evaluation", "MessageSquareText", null, 70);
            seedMenu("teaching-feedback", "\u6559\u5b66\u4fe1\u606f\u53cd\u9988", "/information/feedback", "MessageCircle", "evaluation", 71);
            seedMenu("graduation-design", "\u6bd5\u8bbe\u8bba\u6587", "/graduation-design", "FileText", null, 80);
            seedMenu("thesis-grade", "\u8bba\u6587\u6210\u7ee9\u67e5\u770b", "/information/thesis-grade", "Award", "graduation-design", 81);
            seedMenu("teacher", "\u6559\u5e08\u5de5\u4f5c\u53f0", "/teacher", "Presentation", null, 55);
            seedMenu("teacher-offerings", "\u4efb\u8bfe\u8bfe\u7a0b", "/teacher/courses", "BookOpen", "teacher", 56);
            seedMenu("teacher-homeroom-classes", "班主任班级", "/teacher/classes", "UsersRound", "teacher", 57);
            seedMenu("teacher-grades", "\u6210\u7ee9\u5f55\u5165", "/teacher/grades", "SquarePen", "teacher", 57);
            seedMenu("teacher-exams", "\u8003\u8bd5\u5b89\u6392", "/teacher/exams", "CalendarClock", "teacher", 58);
            seedMenu("teacher-evaluations", "\u8bc4\u4ef7\u7ed3\u679c", "/teacher/evaluations", "ChartColumn", "teacher", 59);
            seedMenu("admin", "\u6559\u52a1\u7ba1\u7406", "/admin", "Settings", null, 60);
            seedMenu("admin-classes", "\u73ed\u7ea7\u4e0e\u5b66\u751f", "/admin/classes", "UsersRound", "admin", 60);
            seedMenu("admin-course-offerings", "\u8bfe\u7a0b\u4e0e\u6559\u5b66\u73ed", "/admin/course-offerings", "BookOpenCheck", "admin", 61);
            seedMenu("admin-status-changes", "\u5b66\u7c4d\u5f02\u52a8\u5ba1\u6838", "/admin/status-changes", "FileCheck2", "admin", 62);
            seedMenu("admin-role-permissions", "\u89d2\u8272\u6743\u9650\u7ba1\u7406", "/admin/role-permissions", "ShieldCheck", "admin", 63);
            seedMenu("admin-permission-matrix", "\u6743\u9650\u77e9\u9635", "/admin/permission-matrix", "ShieldCheck", "admin", 64);
            seedMenu("admin-users", "\u7528\u6237\u4e0e\u89d2\u8272", "/admin/users", "UsersRound", "admin", 65);
            seedMenu("admin-evaluations", "\u6559\u5b66\u8bc4\u4ef7\u7edf\u8ba1", "/admin/evaluations", "ChartColumn", "admin", 66);
            seedMenu("admin-grades", "\u6210\u7ee9\u7ba1\u7406", "/admin/grades", "FileSpreadsheet", "admin", 67);
            seedMenu("admin-exams", "\u8003\u8bd5\u7ba1\u7406", "/admin/exams", "ClipboardCheck", "admin", 68);
            seedMenu("admin-notices", "\u901a\u77e5\u516c\u544a", "/admin/notices", "Megaphone", "admin", 69);
            seedMenu("admin-files", "\u6587\u4ef6\u7ba1\u7406", "/admin/files", "FolderOpen", "admin", 70);
            seedMenu("admin-audit-logs", "\u64cd\u4f5c\u5ba1\u8ba1", "/admin/audit-logs", "ScrollText", "admin", 71);
            seedMenu("admin-registration-applications", "\u62a5\u540d\u7533\u8bf7\u5ba1\u6838", "/admin/registration-applications", "FileCheck2", "admin", 72);
            seedMenu("admin-batch-tasks", "\u6279\u91cf\u4efb\u52a1\u4e2d\u5fc3", "/admin/batch-tasks", "ListChecks", "admin", 73);
            seedMenu("admin-data-archive", "\u6570\u636e\u5f52\u6863\u6e05\u7406", "/admin/data-archive", "Archive", "admin", 74);
            seedMenu("admin-system-health", "\u7cfb\u7edf\u5065\u5eb7\u4e2d\u5fc3", "/admin/system-health", "Activity", "admin", 75);
            seedMenu("admin-redis-monitor", "Redis\u72b6\u6001\u76d1\u63a7", "/admin/redis-monitor", "DatabaseZap", "admin", 76);
            seedMenu("admin-course-selection-consistency", "\u9009\u8bfe\u4e00\u81f4\u6027\u62a5\u544a", "/admin/course-selection-consistency", "ListChecks", "admin", 77);
            seedMenu("admin-data-dictionary", "\u6570\u636e\u5b57\u5178", "/admin/data-dictionary", "BookMarked", "admin", 78);
            seedMenu("admin-sensitive-words", "\u654f\u611f\u8bcd\u4e0e\u5185\u5bb9\u5b89\u5168", "/admin/sensitive-words", "ShieldAlert", "admin", 79);
            seedMenu("admin-load-test-reports", "\u538b\u6d4b\u5386\u53f2\u62a5\u544a", "/admin/load-test-reports", "ChartColumnBig", "admin", 80);
            seedMenu("admin-database-browser", "\u6570\u636e\u5e93\u53ea\u8bfb\u6d4f\u89c8", "/admin/database-browser", "TableProperties", "admin", 81);
            seedMenu("admin-ai-sql", "\u81ea\u7136\u8bed\u8a00\u67e5\u5e93", "/admin/ai-sql", "Sparkles", "admin", 82);
            seedMenu("admin-ai-logs", "AI\u8c03\u7528\u65e5\u5fd7", "/admin/ai-logs", "ScrollText", "admin", 83);
            seedMenu("admin-ai-models", "AI\u6a21\u578b\u4e0e\u8054\u7f51\u641c\u7d22", "/admin/ai-models", "BrainCircuit", "admin", 84);
            seedPermission("COURSE_WRITE", "\u8bfe\u7a0b\u5199\u5165", "\u7ef4\u62a4\u8bfe\u7a0b\u548c\u6559\u5b66\u73ed");
            seedPermission("GRADE_READ", "\u6210\u7ee9\u67e5\u770b", "\u67e5\u770b\u6210\u7ee9\u6570\u636e");
            seedPermission("GRADE_WRITE", "\u6210\u7ee9\u5199\u5165", "\u5f55\u5165\u548c\u4fee\u6539\u6210\u7ee9");
            seedPermission("EXAM_WRITE", "\u8003\u8bd5\u5199\u5165", "\u7ef4\u62a4\u8003\u8bd5\u5b89\u6392");
            seedPermission("STATUS_REVIEW", "\u5b66\u7c4d\u5ba1\u6838", "\u5ba1\u6838\u5b66\u7c4d\u5f02\u52a8");
            seedPermission("ROLE_PERMISSION_WRITE", "\u89d2\u8272\u6743\u9650", "\u7ef4\u62a4\u89d2\u8272\u83dc\u5355\u548c\u6743\u9650");
            seedPermission("USER_WRITE", "\u7528\u6237\u5199\u5165", "\u7ef4\u62a4\u7528\u6237\u548c\u89d2\u8272");
            seedPermission("NOTICE_WRITE", "\u901a\u77e5\u5199\u5165", "\u53d1\u5e03\u901a\u77e5\u516c\u544a");
            seedPermission("AUDIT_READ", "\u5ba1\u8ba1\u67e5\u770b", "\u67e5\u770b\u64cd\u4f5c\u65e5\u5fd7");
            retireMenu("info-profile-query");
            retireMenu("info-personal-schedule");
            retireMenu("info-free-classroom");
            retireMenu("info-grade-query");
            retireMenu("info-exam-query");
            retireMenu("info-weekly-schedule");
            seedRoleMenus(studentRole.getCode(), List.of(
                    "dashboard",
                    "ai-assistant",
                    "ai-chat",
                    "student",
                    "student-profile",
                    "student-class",
                    "student-status-change",
                    "registration",
                    "registration-minor",
                    "registration-retake",
                    "registration-credit-internal",
                    "registration-credit-external",
                    "registration-score-bonus",
                    "registration-stream-confirm",
                    "registration-direction-confirm",
                    "course",
                    "course-selection",
                    "schedule-personal",
                    "classroom-free",
                    "info-query",
                    "info-warning",
                    "info-graduation-audit",
                    "info-class-schedule",
                    "info-roster",
                    "info-academic-progress",
                    "ai-academic-profile",
                    "info-teaching-plan",
                    "grade",
                    "grade-query",
                    "exam-query",
                    "evaluation",
                    "teaching-feedback",
                    "graduation-design",
                    "thesis-grade"
            ));
            seedRoleMenus(adminRole.getCode(), List.of(
                    "dashboard",
                    "ai-assistant",
                    "ai-chat",
                    "admin",
                    "admin-classes",
                    "admin-course-offerings",
                    "admin-status-changes",
                    "admin-role-permissions",
                    "admin-permission-matrix",
                    "admin-users",
                    "admin-evaluations",
                    "admin-grades",
                    "admin-exams",
                    "admin-notices",
                    "admin-files",
                    "admin-audit-logs",
                    "admin-registration-applications",
                    "admin-batch-tasks",
                    "admin-data-archive",
                    "admin-system-health",
                    "admin-redis-monitor",
                    "admin-course-selection-consistency",
                    "admin-data-dictionary",
                    "admin-sensitive-words",
                    "admin-load-test-reports",
                    "admin-database-browser",
                    "admin-ai-sql",
                    "admin-ai-logs",
                    "admin-ai-models"
            ));
            removeRoleMenus(adminRole.getCode(), List.of(
                    "student",
                    "student-profile",
                    "student-class",
                    "student-status-change",
                    "registration",
                    "registration-minor",
                    "registration-retake",
                    "registration-credit-internal",
                    "registration-credit-external",
                    "registration-score-bonus",
                    "registration-stream-confirm",
                    "registration-direction-confirm",
                    "course",
                    "course-selection",
                    "schedule-personal",
                    "classroom-free",
                    "info-query",
                    "info-warning",
                    "info-graduation-audit",
                    "info-class-schedule",
                    "info-roster",
                    "info-academic-progress",
                    "info-teaching-plan",
                    "grade",
                    "grade-query",
                    "exam-query",
                    "evaluation",
                    "teaching-feedback",
                    "graduation-design",
                    "thesis-grade",
                    "teacher",
                    "teacher-offerings",
                    "teacher-homeroom-classes",
                    "teacher-grades",
                    "teacher-exams",
                    "teacher-evaluations"
            ));
            seedRolePermissions(adminRole.getCode(), List.of(
                    "COURSE_WRITE",
                    "GRADE_READ",
                    "GRADE_WRITE",
                    "EXAM_WRITE",
                    "STATUS_REVIEW",
                    "ROLE_PERMISSION_WRITE",
                    "USER_WRITE",
                    "NOTICE_WRITE",
                    "AUDIT_READ"
            ));
            seedRolePermissions(teacherRole.getCode(), List.of("GRADE_READ"));
            seedRoleMenus(teacherRole.getCode(), List.of(
                    "dashboard",
                    "ai-assistant",
                    "ai-chat",
                    "teacher",
                    "teacher-offerings",
                    "teacher-homeroom-classes",
                    "teacher-grades",
                    "teacher-exams",
                    "teacher-evaluations"
            ));

            Course softwareEngineering = seedCourse("CS301", "\u8f6f\u4ef6\u5de5\u7a0b", 3, "\u4e13\u4e1a\u5fc5\u4fee");
            Course database = seedCourse("CS305", "\u6570\u636e\u5e93\u7cfb\u7edf", 3, "\u4e13\u4e1a\u5fc5\u4fee");
            Course english = seedCourse("EN201", "\u5927\u5b66\u82f1\u8bed", 2, "\u516c\u5171\u5fc5\u4fee");
            Instant openStart = Instant.now().minusSeconds(3600);
            Instant openEnd = Instant.now().plusSeconds(7 * 24 * 3600);
            seedOffering(softwareEngineering, "\u5f20\u8001\u5e08", "2025-2026-2", 40, "\u5468\u4e00 1-2\u8282", "A\u6559\u5b66\u697c 302", openStart, openEnd);
            seedOffering(database, "\u674e\u8001\u5e08", "2025-2026-2", 45, "\u5468\u4e09 3-4\u8282", "B\u6559\u5b66\u697c 208", openStart, openEnd);
            seedOffering(english, "\u738b\u8001\u5e08", "2025-2026-2", 60, "\u5468\u4e94 5-6\u8282", "C\u6559\u5b66\u697c 101", openStart, openEnd);

            Student student = studentRepository.findByStudentNo("23111141")
                    .orElseThrow(() -> new IllegalStateException("Seed student missing"));
            seedGrade(student, database, "2025-2026-2", 91, "4.10", "\u6b63\u5e38\u8003\u8bd5");
            seedGrade(student, softwareEngineering, "2025-2026-2", 88, "3.80", "\u6b63\u5e38\u8003\u8bd5");
            seedGrade(student, english, "2025-2026-1", 93, "4.30", "\u6b63\u5e38\u8003\u8bd5");

            CourseOffering databaseOffering = findOffering("CS305", "2025-2026-2");
            CourseOffering englishOffering = findOffering("EN201", "2025-2026-2");
            seedExam(englishOffering, LocalDateTime.of(2026, 6, 18, 9, 0), "B\u6559\u5b66\u697c 201", "18", "\u671f\u672b\u8003\u8bd5", "\u5df2\u5b89\u6392");
            seedExam(databaseOffering, LocalDateTime.of(2026, 6, 21, 14, 0), "A\u6559\u5b66\u697c 302", "27", "\u671f\u672b\u8003\u8bd5", "\u5df2\u5b89\u6392");

            seedClassroom("\u4e3b\u6821\u533a", "A\u6559\u5b66\u697c", "A-302", 72, "\u591a\u5a92\u4f53", "1-2\u8282");
            seedClassroom("\u4e3b\u6821\u533a", "B\u6559\u5b66\u697c", "B-208", 60, "\u666e\u901a\u6559\u5ba4", "3-4\u8282");
            seedClassroom("\u4e1c\u533a", "C\u6559\u5b66\u697c", "C-101", 90, "\u9636\u68af\u6559\u5ba4", "5-6\u8282");
            seedRichDemoData(studentRole, openStart, openEnd);
            seedFixedTestAccounts(adminRole, teacherRole, studentRole);
            seedAcademicClassesFromStudents();
            seedInformationCenterData(student);
        }

        private void seedFixedTestAccounts(SysRole adminRole, SysRole teacherRole, SysRole studentRole) {
            for (int i = 1; i <= 6; i++) {
                seedUserWithRole("admin" + String.format("%03d", i), "管理员测试" + i, adminRole);
            }

            List<String> teacherNames = List.of("张老师", "李老师", "王老师", "赵老师", "刘老师", "陈老师");
            for (int i = 1; i <= 6; i++) {
                seedUserWithRole("teacher" + String.format("%03d", i), teacherNames.get(i - 1), teacherRole);
            }

            List<CourseOffering> softwareOfferings = List.of(
                    findOffering("SE101", "2025-2026-2"),
                    findOffering("SE102", "2025-2026-2"),
                    findOffering("SE201", "2025-2026-2"),
                    findOffering("SE202", "2025-2026-2"),
                    findOffering("SE301", "2025-2026-2")
            );
            for (int i = 1; i <= 6; i++) {
                String username = "student" + String.format("%03d", i);
                Student student = seedDemoStudent(
                        username,
                        "学生测试" + i,
                        "信息科学与工程学院",
                        "软件工程",
                        "软件工程 23-1",
                        studentRole
                );
                softwareOfferings.forEach(offering -> seedSelection(student, offering));
                seedDemoGrades(student, softwareOfferings);
                seedInformationCenterData(student);
            }
        }

        private SysUser seedUserWithRole(String username, String displayName, SysRole role) {
            SysUser user = userRepository.findByUsername(username)
                    .orElseGet(() -> new SysUser(username, passwordEncoder.encode("123456"), displayName));
            user.addRole(role);
            return userRepository.save(user);
        }

        private void seedRichDemoData(SysRole studentRole, Instant openStart, Instant openEnd) {
            List<CollegePlan> colleges = List.of(
                    new CollegePlan("信息科学与工程学院", List.of("计算机科学与技术", "人工智能", "无人机", "软件工程", "电子信息工程", "通信工程")),
                    new CollegePlan("医学院", List.of("护理学", "康复治疗学")),
                    new CollegePlan("经济管理学院", List.of("财务管理", "市场营销")),
                    new CollegePlan("艺术学院", List.of("视觉传达设计", "数字媒体艺术"))
            );
            int accountIndex = 1;
            for (CollegePlan college : colleges) {
                for (String major : college.majors()) {
                    List<CourseOffering> weeklyOfferings = seedMajorWeeklyOfferings(major, openStart, openEnd);
                    for (int classIndex = 1; classIndex <= 2; classIndex++) {
                        String className = major + " 23-" + classIndex;
                        int generatedStudents = className.equals("软件工程 23-1") ? 4 : 5;
                        for (int studentIndex = 1; studentIndex <= generatedStudents; studentIndex++) {
                            String username = "23" + String.format("%04d", accountIndex++);
                            Student demoStudent = seedDemoStudent(
                                    username,
                                    "学生" + username.substring(username.length() - 4),
                                    college.name(),
                                    major,
                                    className,
                                    studentRole
                            );
                            weeklyOfferings.forEach(offering -> seedSelection(demoStudent, offering));
                            seedDemoGrades(demoStudent, weeklyOfferings);
                            seedInformationCenterData(demoStudent);
                        }
                    }
                    if (major.equals("软件工程")) {
                        studentRepository.findByStudentNo("23111141").ifPresent(existing -> {
                            weeklyOfferings.forEach(offering -> seedSelection(existing, offering));
                            seedDemoGrades(existing, weeklyOfferings);
                        });
                    }
                }
            }
        }

        private List<CourseOffering> seedMajorWeeklyOfferings(String major, Instant openStart, Instant openEnd) {
            String prefix = majorCodePrefix(major);
            String category = major.equals("大学英语") ? "公共必修" : "专业必修";
            List<Course> courses = List.of(
                    seedCourse(prefix + "101", major + "导论", 2, category),
                    seedCourse(prefix + "102", major + "程序设计基础", 3, category),
                    seedCourse(prefix + "201", major + "核心技术", 3, category),
                    seedCourse(prefix + "202", major + "综合实践", 2, "实践教学"),
                    seedCourse(prefix + "301", major + "项目实训", 2, "实践教学")
            );
            List<String> days = List.of("周一 1-2节", "周二 3-4节", "周三 1-2节", "周四 5-6节", "周五 3-4节");
            List<String> rooms = List.of("A教学楼 301", "A教学楼 302", "B教学楼 208", "C教学楼 101", "实训中心 405");
            List<String> teachers = List.of("张老师", "李老师", "王老师", "赵老师", "刘老师");
            for (int i = 0; i < courses.size(); i++) {
                seedOffering(courses.get(i), teachers.get(i), "2025-2026-2", 80, days.get(i), rooms.get(i), openStart, openEnd);
            }
            return courses.stream()
                    .map(course -> findOffering(course.getCode(), "2025-2026-2"))
                    .toList();
        }

        private Student seedDemoStudent(String username, String displayName, String college, String major, String className, SysRole studentRole) {
            SysUser user = userRepository.findByUsername(username)
                    .orElseGet(() -> new SysUser(username, passwordEncoder.encode("123456"), displayName));
            user.addRole(studentRole);
            if (user.getId() == null) {
                userRepository.save(user);
            }
            return studentRepository.findByStudentNo(username)
                    .orElseGet(() -> {
                        SysUser persistedUser = userRepository.findByUsername(username)
                                .orElseThrow(() -> new IllegalStateException("Seed user missing: " + username));
                        Student student = new Student(persistedUser, username, college, major, className, "2023", "在籍");
                        student.updateContact("1380000" + username.substring(username.length() - 4), username + "@student.example.com", "天津市武清区");
                        return studentRepository.save(student);
                    });
        }

        private void seedSelection(Student student, CourseOffering offering) {
            if (!selectionRepository.existsByStudentAndOffering(student, offering)) {
                selectionRepository.save(new CourseSelection(student, offering));
            }
        }

        private void seedDemoGrades(Student student, List<CourseOffering> offerings) {
            int score = 82;
            for (CourseOffering offering : offerings) {
                seedGrade(student, offering.getCourse(), offering.getTerm(), score, String.format("%.2f", Math.min(4.5, (score - 50) / 10.0)), "正常考试");
                score = Math.min(96, score + 2);
            }
        }

        private String majorCodePrefix(String major) {
            return switch (major) {
                case "计算机科学与技术" -> "CST";
                case "人工智能" -> "AI";
                case "无人机" -> "UAV";
                case "软件工程" -> "SE";
                case "电子信息工程" -> "EIE";
                case "通信工程" -> "CE";
                case "护理学" -> "NUR";
                case "康复治疗学" -> "RT";
                case "财务管理" -> "FM";
                case "市场营销" -> "MKT";
                case "视觉传达设计" -> "VCD";
                case "数字媒体艺术" -> "DMA";
                default -> "GEN";
            };
        }

        private record CollegePlan(String name, List<String> majors) {
        }

        private void seedInformationCenterData(Student student) {
            Integer warningCount = jdbcTemplate.queryForObject("select count(*) from academic_warning where student_id = ?", Integer.class, student.getId());
            if (warningCount == null || warningCount == 0) {
                jdbcTemplate.update("""
                        insert into academic_warning (student_id, term, level, reason, status, created_at)
                        values (?, ?, ?, ?, ?, ?)
                        """, student.getId(), "2025-2026-2", "提示", "当前学分进度正常，需按时完成教学评价和考试确认。", "已确认", Instant.now());
            }

            Integer auditCount = jdbcTemplate.queryForObject("select count(*) from graduation_audit where student_id = ?", Integer.class, student.getId());
            if (auditCount == null || auditCount == 0) {
                jdbcTemplate.update("insert into graduation_audit (student_id, audit_item, required_value, current_value, passed, remark, updated_at) values (?, ?, ?, ?, ?, ?, ?)",
                        student.getId(), "总学分", "160", "98", false, "仍需完成后续专业课和实践环节。", Instant.now());
                jdbcTemplate.update("insert into graduation_audit (student_id, audit_item, required_value, current_value, passed, remark, updated_at) values (?, ?, ?, ?, ?, ?, ?)",
                        student.getId(), "必修课通过率", "100%", "已通过当前应修课程", true, "当前阶段符合要求。", Instant.now());
            }

            Integer planCount = jdbcTemplate.queryForObject("select count(*) from teaching_plan_item where major = ? and grade = ?", Integer.class, student.getMajor(), student.getGrade());
            if (planCount == null || planCount == 0) {
                jdbcTemplate.update("insert into teaching_plan_item (grade, major, term, course_code, course_name, credit, course_type, assessment_type) values (?, ?, ?, ?, ?, ?, ?, ?)",
                        student.getGrade(), student.getMajor(), "2025-2026-1", "EN201", "大学英语", 2, "公共必修", "考试");
                jdbcTemplate.update("insert into teaching_plan_item (grade, major, term, course_code, course_name, credit, course_type, assessment_type) values (?, ?, ?, ?, ?, ?, ?, ?)",
                        student.getGrade(), student.getMajor(), "2025-2026-2", "CS301", "软件工程", 3, "专业必修", "考试");
                jdbcTemplate.update("insert into teaching_plan_item (grade, major, term, course_code, course_name, credit, course_type, assessment_type) values (?, ?, ?, ?, ?, ?, ?, ?)",
                        student.getGrade(), student.getMajor(), "2025-2026-2", "CS305", "数据库系统", 3, "专业必修", "考试");
            }

            Integer thesisCount = jdbcTemplate.queryForObject("select count(*) from thesis_grade where student_id = ?", Integer.class, student.getId());
            if (thesisCount == null || thesisCount == 0) {
                jdbcTemplate.update("""
                        insert into thesis_grade
                          (student_id, title, advisor, proposal_score, midterm_score, defense_score, final_score, grade_level, status, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, student.getId(), "基于 Vue 与 Spring Boot 的教务系统设计与实现", "张老师", 88, 90, null, null, null, "进行中", Instant.now());
            }
        }

        private void seedMenu(String code, String title, String path, String icon, String parentCode, int sortOrder) {
            if (!menuRepository.existsByCode(code)) {
                menuRepository.save(new SysMenu(code, title, path, icon, parentCode, sortOrder));
            } else {
                jdbcTemplate.update("""
                                update sys_menu
                                set title = ?, path = ?, icon = ?, parent_code = ?, sort_order = ?
                                where code = ?
                                """,
                        title, path, icon, parentCode, sortOrder, code);
            }
        }

        private void retireMenu(String code) {
            jdbcTemplate.update("delete from sys_role_menu where menu_id in (select id from sys_menu where code = ?)", code);
            jdbcTemplate.update("delete from sys_menu where code = ?", code);
        }

        private void seedRoleMenus(String roleCode, List<String> menuCodes) {
            Long roleId = jdbcTemplate.queryForObject(
                    "select id from sys_role where code = ?",
                    Long.class,
                    roleCode
            );
            if (roleId == null) {
                return;
            }
            for (String menuCode : menuCodes) {
                Integer existingCount = jdbcTemplate.queryForObject("""
                        select count(*)
                        from sys_role_menu rm
                        join sys_menu m on m.id = rm.menu_id
                        where rm.role_id = ?
                          and m.code = ?
                        """, Integer.class, roleId, menuCode);
                if (existingCount == null || existingCount == 0) {
                    jdbcTemplate.update("""
                            insert into sys_role_menu (role_id, menu_id)
                            select ?, id
                            from sys_menu
                            where code = ?
                            """, roleId, menuCode);
                }
            }
        }

        private void removeRoleMenus(String roleCode, List<String> menuCodes) {
            Long roleId = jdbcTemplate.queryForObject(
                    "select id from sys_role where code = ?",
                    Long.class,
                    roleCode
            );
            if (roleId == null || menuCodes.isEmpty()) {
                return;
            }
            for (String menuCode : menuCodes) {
                jdbcTemplate.update("""
                        delete rm
                        from sys_role_menu rm
                        join sys_menu m on m.id = rm.menu_id
                        where rm.role_id = ?
                          and m.code = ?
                        """, roleId, menuCode);
            }
        }

        private void removeUserRole(String username, String roleCode) {
            jdbcTemplate.update("""
                    delete ur
                    from sys_user_role ur
                    join sys_user u on u.id = ur.user_id
                    join sys_role r on r.id = ur.role_id
                    where u.username = ?
                      and r.code = ?
                    """, username, roleCode);
        }

        private void seedPermission(String code, String name, String description) {
            Integer existingCount = jdbcTemplate.queryForObject("select count(*) from sys_permission where code = ?", Integer.class, code);
            if (existingCount == null || existingCount == 0) {
                jdbcTemplate.update("insert into sys_permission (code, name, description) values (?, ?, ?)", code, name, description);
            }
        }

        private void seedRolePermissions(String roleCode, List<String> permissionCodes) {
            Long roleId = jdbcTemplate.queryForObject("select id from sys_role where code = ?", Long.class, roleCode);
            if (roleId == null) {
                return;
            }
            for (String permissionCode : permissionCodes) {
                Integer existingCount = jdbcTemplate.queryForObject("""
                        select count(*)
                        from sys_role_permission rp
                        join sys_permission p on p.id = rp.permission_id
                        where rp.role_id = ?
                          and p.code = ?
                        """, Integer.class, roleId, permissionCode);
                if (existingCount == null || existingCount == 0) {
                    jdbcTemplate.update("""
                            insert into sys_role_permission (role_id, permission_id)
                            select ?, id
                            from sys_permission
                            where code = ?
                            """, roleId, permissionCode);
                }
            }
        }

        private Course seedCourse(String code, String name, int credit, String category) {
            return courseRepository.findByCode(code)
                    .orElseGet(() -> courseRepository.save(new Course(code, name, credit, category)));
        }

        private void seedOffering(
                Course course,
                String teacherName,
                String term,
                int capacity,
                String scheduleText,
                String classroom,
                Instant selectionStartAt,
                Instant selectionEndAt
        ) {
            boolean exists = offeringRepository.findByTermOrderByCourseCodeAsc(term).stream()
                    .anyMatch(offering -> offering.getCourse().getCode().equals(course.getCode()));
            if (!exists) {
                offeringRepository.save(new CourseOffering(
                        course,
                        teacherName,
                        term,
                        capacity,
                        scheduleText,
                        classroom,
                        selectionStartAt,
                        selectionEndAt
                ));
            }
        }

        private void seedGrade(Student student, Course course, String term, int score, String gradePoint, String examType) {
            if (!gradeRepository.existsByStudentAndCourseAndTermAndExamType(student, course, term, examType)) {
                gradeRepository.save(new AcademicGrade(student, course, term, score, new BigDecimal(gradePoint), examType));
            }
        }

        private void seedExam(CourseOffering offering, LocalDateTime examTime, String room, String seatNo, String examType, String status) {
            if (!examScheduleRepository.existsByCourseOfferingAndExamTime(offering, examTime)) {
                examScheduleRepository.save(new ExamSchedule(offering, examTime, room, seatNo, examType, status));
            }
        }

        private void seedClassroom(String campus, String building, String room, int capacity, String roomType, String availableSlot) {
            if (!classroomRepository.existsByRoom(room)) {
                classroomRepository.save(new Classroom(campus, building, room, capacity, roomType, availableSlot));
            }
        }

        private void seedAcademicClassesFromStudents() {
            List<StudentClassSeedRow> rows = jdbcTemplate.query("""
                            select college, major, grade, class_name
                            from student
                            where class_name is not null
                              and class_name <> ''
                              and class_name <> '未分班'
                            group by college, major, grade, class_name
                            order by college, major, grade, class_name
                            """,
                    (rs, rowNum) -> new StudentClassSeedRow(
                            rs.getString("college"),
                            rs.getString("major"),
                            rs.getString("grade"),
                            rs.getString("class_name")
                    ));
            for (StudentClassSeedRow row : rows) {
                Integer existingCount = jdbcTemplate.queryForObject(
                        "select count(*) from academic_class where class_name = ?",
                        Integer.class,
                        row.className()
                );
                if (existingCount == null || existingCount == 0) {
                    jdbcTemplate.update("""
                                    insert into academic_class (college, major, grade, class_name, advisor, created_at, updated_at)
                                    values (?, ?, ?, ?, ?, ?, ?)
                                    """,
                            row.college(), row.major(), row.grade(), row.className(), "张老师", Instant.now(), Instant.now());
                }
            }
        }

        private record StudentClassSeedRow(String college, String major, String grade, String className) {
        }

        private CourseOffering findOffering(String courseCode, String term) {
            return offeringRepository.findByTermOrderByCourseCodeAsc(term).stream()
                    .filter(offering -> offering.getCourse().getCode().equals(courseCode))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Seed offering missing: " + courseCode));
        }
    }
}
