package weidonglang.tianshiwebside.student;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import weidonglang.tianshiwebside.common.api.ApiResponse;
import weidonglang.tianshiwebside.common.error.BusinessException;
import weidonglang.tianshiwebside.common.error.ErrorCode;

import java.util.List;

@RestController
@RequestMapping("/api/students/me/class")
public class StudentClassController {
    private final JdbcTemplate jdbcTemplate;

    public StudentClassController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<StudentClassInfoResponse> myClass(Authentication authentication) {
        String username = authenticatedUsername(authentication);
        List<StudentClassBase> bases = jdbcTemplate.query("""
                        select
                          s.class_name,
                          s.college,
                          s.major,
                          s.grade,
                          ac.advisor,
                          ht.display_name as homeroom_teacher_name,
                          (select count(*) from student member where member.class_name = s.class_name) as student_count
                        from student s
                        join sys_user u on u.id = s.user_id
                        left join academic_class ac on ac.class_name = s.class_name
                        left join sys_user ht on ht.id = ac.homeroom_teacher_user_id
                        where u.username = ?
                        """,
                (rs, rowNum) -> new StudentClassBase(
                        rs.getString("class_name"),
                        rs.getString("college"),
                        rs.getString("major"),
                        rs.getString("grade"),
                        rs.getString("advisor"),
                        rs.getString("homeroom_teacher_name"),
                        rs.getLong("student_count")
                ),
                username);
        if (bases.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学生档案不存在");
        }
        StudentClassBase base = bases.get(0);
        List<StudentClassMemberRow> members = jdbcTemplate.query("""
                        select s.student_no, u.display_name as name, s.status
                        from student s
                        join sys_user u on u.id = s.user_id
                        where s.class_name = ?
                        order by s.student_no asc
                        """,
                (rs, rowNum) -> new StudentClassMemberRow(
                        rs.getString("student_no"),
                        rs.getString("name"),
                        rs.getString("status")
                ),
                base.className());
        return ApiResponse.success(new StudentClassInfoResponse(
                base.className(),
                base.college(),
                base.major(),
                base.grade(),
                base.advisor(),
                base.homeroomTeacherName(),
                base.studentCount(),
                members
        ));
    }

    private String authenticatedUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return authentication.getName();
    }

    private record StudentClassBase(String className, String college, String major, String grade, String advisor,
                                    String homeroomTeacherName, Long studentCount) {
    }

    public record StudentClassInfoResponse(String className, String college, String major, String grade, String advisor,
                                           String homeroomTeacherName, Long studentCount,
                                           List<StudentClassMemberRow> members) {
    }

    public record StudentClassMemberRow(String studentNo, String name, String status) {
    }
}
