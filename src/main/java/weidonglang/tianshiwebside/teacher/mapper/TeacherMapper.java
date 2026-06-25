package weidonglang.tianshiwebside.teacher.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import weidonglang.tianshiwebside.academic.mapper.AcademicAdminMapper.ExamAdminRow;
import weidonglang.tianshiwebside.academic.mapper.AcademicAdminMapper.ExamCommand;
import weidonglang.tianshiwebside.academic.mapper.AcademicAdminMapper.GradeAdminRow;
import weidonglang.tianshiwebside.academic.mapper.AcademicAdminMapper.GradeCommand;
import weidonglang.tianshiwebside.evaluation.mapper.EvaluationSummaryRow;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface TeacherMapper {
    @Select("""
            select display_name
            from sys_user
            where username = #{username}
            """)
    String findDisplayNameByUsername(@Param("username") String username);

    @Select("""
            select
              co.id as offering_id,
              c.code as course_code,
              c.name as course_name,
              c.credit as credit,
              c.category as category,
              co.teacher_name as teacher_name,
              co.term as term,
              co.capacity as capacity,
              (select count(*) from course_selection cs where cs.offering_id = co.id) as selected_count,
              co.schedule_text as schedule_text,
              co.classroom as classroom
            from course_offering co
            join course c on c.id = co.course_id
            where co.teacher_name = #{teacherName}
              and (#{term} is null or co.term = #{term})
            order by co.term desc, c.code asc
            """)
    List<TeacherOfferingRow> findOfferings(@Param("teacherName") String teacherName, @Param("term") String term);

    @Select("""
            select
              ag.id as grade_id,
              co.id as offering_id,
              s.student_no as student_no,
              u.display_name as student_name,
              c.id as course_id,
              c.code as course_code,
              c.name as course_name,
              co.term as term,
              ag.score as score,
              ag.grade_point as grade_point,
              coalesce(ag.exam_type, '正常考试') as exam_type,
              coalesce(ag.grade_status, '未录入') as grade_status,
              coalesce(ag.locked, false) as locked
            from course_selection cs
            join student s on s.id = cs.student_id
            join sys_user u on u.id = s.user_id
            join course_offering co on co.id = cs.offering_id
            join course c on c.id = co.course_id
            left join academic_grade ag
              on ag.student_id = s.id
             and ag.course_id = c.id
             and ag.term = co.term
            where co.teacher_name = #{teacherName}
              and (#{term} is null or co.term = #{term})
              and (#{offeringId} is null or co.id = #{offeringId})
              and (
                #{keyword} is null
                or s.student_no like #{keyword}
                or u.display_name like #{keyword}
                or c.name like #{keyword}
              )
            order by co.term desc, c.code asc, s.student_no asc
            limit #{size} offset #{offset}
            """)
    List<TeacherGradeEntryRow> findGradeEntries(@Param("teacherName") String teacherName, @Param("term") String term,
                                                @Param("offeringId") Long offeringId, @Param("keyword") String keyword,
                                                @Param("size") int size, @Param("offset") int offset);

    @Select("""
            select count(*)
            from course_selection cs
            join student s on s.id = cs.student_id
            join sys_user u on u.id = s.user_id
            join course_offering co on co.id = cs.offering_id
            join course c on c.id = co.course_id
            where co.teacher_name = #{teacherName}
              and (#{term} is null or co.term = #{term})
              and (#{offeringId} is null or co.id = #{offeringId})
              and (
                #{keyword} is null
                or s.student_no like #{keyword}
                or u.display_name like #{keyword}
                or c.name like #{keyword}
              )
            """)
    long countGradeEntries(@Param("teacherName") String teacherName, @Param("term") String term,
                           @Param("offeringId") Long offeringId, @Param("keyword") String keyword);

    @Select("""
            select
              s.student_no as student_no,
              u.display_name as student_name,
              s.class_name as class_name,
              s.major as major,
              cs.selected_at as selected_at,
              coalesce(ag.grade_status, '未录入') as grade_status
            from course_selection cs
            join student s on s.id = cs.student_id
            join sys_user u on u.id = s.user_id
            join course_offering co on co.id = cs.offering_id
            join course c on c.id = co.course_id
            left join academic_grade ag
              on ag.student_id = s.id
             and ag.course_id = c.id
             and ag.term = co.term
            where co.id = #{offeringId}
              and co.teacher_name = #{teacherName}
            order by s.student_no asc
            """)
    List<TeacherOfferingStudentRow> findOfferingStudents(@Param("teacherName") String teacherName,
                                                         @Param("offeringId") Long offeringId);

    @Select("""
            select s.id
            from course_selection cs
            join student s on s.id = cs.student_id
            join course_offering co on co.id = cs.offering_id
            where co.id = #{offeringId}
              and co.teacher_name = #{teacherName}
              and s.student_no = #{studentNo}
            """)
    Long findOwnedSelectedStudentId(@Param("teacherName") String teacherName, @Param("offeringId") Long offeringId,
                                    @Param("studentNo") String studentNo);

    @Select("""
            select c.id
            from course_offering co
            join course c on c.id = co.course_id
            where co.id = #{offeringId}
              and co.teacher_name = #{teacherName}
            """)
    Long findOwnedOfferingCourseId(@Param("teacherName") String teacherName, @Param("offeringId") Long offeringId);

    @Select("""
            select ag.id
            from academic_grade ag
            join course_offering co on co.course_id = ag.course_id and co.term = ag.term
            where ag.student_id = #{studentId}
              and ag.course_id = #{courseId}
              and ag.term = #{term}
              and co.id = #{offeringId}
              and co.teacher_name = #{teacherName}
            """)
    Long findOwnedGradeId(@Param("teacherName") String teacherName, @Param("offeringId") Long offeringId,
                          @Param("studentId") Long studentId, @Param("courseId") Long courseId, @Param("term") String term);

    @Select("""
            select count(*)
            from academic_grade ag
            join course_offering co on co.course_id = ag.course_id and co.term = ag.term
            where ag.id = #{gradeId}
              and co.teacher_name = #{teacherName}
            """)
    int countOwnedGrade(@Param("teacherName") String teacherName, @Param("gradeId") Long gradeId);

    @Select("""
            select count(*)
            from academic_grade
            where id = #{gradeId}
              and locked = true
            """)
    int countLockedGrade(@Param("gradeId") Long gradeId);

    @Select("select score from academic_grade where id = #{gradeId}")
    Integer findGradeScore(@Param("gradeId") Long gradeId);

    @Insert("""
            insert into academic_grade (student_id, course_id, term, score, grade_point, exam_type, grade_status, locked)
            values (#{studentId}, #{courseId}, #{term}, #{score}, #{gradePoint}, #{examType}, #{gradeStatus}, #{locked})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertGrade(GradeCommand command);

    @Update("""
            update academic_grade
            set score = #{score},
                grade_point = #{gradePoint},
                exam_type = #{examType},
                grade_status = #{gradeStatus},
                locked = #{locked}
            where id = #{id}
            """)
    int updateGrade(GradeCommand command);

    @Insert("""
            insert into grade_change_log (grade_id, old_score, new_score, reason, operator, operator_role, trace_id, created_at)
            values (#{gradeId}, #{oldScore}, #{newScore}, #{reason}, #{operator}, #{operatorRole}, #{traceId}, current_timestamp)
            """)
    int insertGradeChangeLog(@Param("gradeId") Long gradeId,
                             @Param("oldScore") Integer oldScore,
                             @Param("newScore") Integer newScore,
                             @Param("reason") String reason,
                             @Param("operator") String operator,
                             @Param("operatorRole") String operatorRole,
                             @Param("traceId") String traceId);

    @Select("""
            select
              es.id as exam_id,
              co.id as offering_id,
              c.code as course_code,
              c.name as course_name,
              co.teacher_name as teacher_name,
              co.term as term,
              es.exam_time as exam_time,
              es.room as room,
              es.seat_no as seat_no,
              es.exam_type as exam_type,
              es.status as status,
              es.invigilator as invigilator
            from exam_schedule es
            join course_offering co on co.id = es.course_offering_id
            join course c on c.id = co.course_id
            where co.teacher_name = #{teacherName}
              and (#{term} is null or co.term = #{term})
            order by es.exam_time asc
            """)
    List<ExamAdminRow> findExams(@Param("teacherName") String teacherName, @Param("term") String term);

    @Select("""
            select count(*)
            from course_offering
            where id = #{offeringId}
              and teacher_name = #{teacherName}
            """)
    int countOwnedOffering(@Param("teacherName") String teacherName, @Param("offeringId") Long offeringId);

    @Select("""
            select count(*)
            from exam_schedule es
            join course_offering co on co.id = es.course_offering_id
            where es.id = #{examId}
              and co.teacher_name = #{teacherName}
            """)
    int countOwnedExam(@Param("teacherName") String teacherName, @Param("examId") Long examId);

    @Insert("""
            insert into exam_schedule (course_offering_id, exam_time, room, seat_no, exam_type, status, invigilator)
            values (#{offeringId}, #{examTime}, #{room}, #{seatNo}, #{examType}, #{status}, #{invigilator})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertExam(ExamCommand command);

    @Update("""
            update exam_schedule
            set course_offering_id = #{offeringId},
                exam_time = #{examTime},
                room = #{room},
                seat_no = #{seatNo},
                exam_type = #{examType},
                status = #{status},
                invigilator = #{invigilator}
            where id = #{id}
            """)
    int updateExam(ExamCommand command);

    @Delete("delete from exam_schedule where id = #{examId}")
    int deleteExam(@Param("examId") Long examId);

    @Select("""
            select
              co.id as offering_id,
              c.code as course_code,
              c.name as course_name,
              co.teacher_name as teacher_name,
              co.term as term,
              (select count(*) from course_selection cs where cs.offering_id = co.id) as selected_count,
              count(te.id) as submitted_count,
              avg(te.teaching_score) as average_teaching_score,
              avg(te.content_score) as average_content_score,
              avg(te.interaction_score) as average_interaction_score,
              avg(te.overall_score) as average_overall_score
            from course_offering co
            join course c on c.id = co.course_id
            left join teaching_evaluation te on te.offering_id = co.id
            where co.teacher_name = #{teacherName}
              and (#{term} is null or co.term = #{term})
            group by co.id, c.code, c.name, co.teacher_name, co.term
            order by co.term desc, c.code asc
            """)
    List<EvaluationSummaryRow> findEvaluationSummaries(@Param("teacherName") String teacherName, @Param("term") String term);

    record TeacherOfferingRow(Long offeringId, String courseCode, String courseName, Integer credit, String category,
                              String teacherName, String term, Integer capacity, Long selectedCount,
                              String scheduleText, String classroom) {
    }

    record TeacherGradeEntryRow(Long gradeId, Long offeringId, String studentNo, String studentName, Long courseId,
                                String courseCode, String courseName, String term, Integer score, BigDecimal gradePoint,
                                String examType, String gradeStatus, Boolean locked) {
    }

    record TeacherOfferingStudentRow(String studentNo, String studentName, String className, String major,
                                     java.time.Instant selectedAt, String gradeStatus) {
    }
}
