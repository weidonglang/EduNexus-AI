package weidonglang.tianshiwebside.course.mapper;

import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.List;

@Mapper
public interface AdminCourseMapper {
    @Select("""
            select
              id as course_id,
              code,
              name,
              credit,
              category
            from course
            order by code asc
            """)
    List<AdminCourseRow> findCourses();

    @Select("""
            select
              id as course_id,
              code,
              name,
              credit,
              category
            from course
            where id = #{courseId}
            """)
    AdminCourseRow findCourseById(@Param("courseId") Long courseId);

    @Select("""
            select count(*)
            from course
            where code = #{code}
            """)
    int countCourseByCode(@Param("code") String code);

    @Select("""
            select count(*)
            from course
            where code = #{code}
              and id <> #{courseId}
            """)
    int countCourseByCodeExceptId(@Param("code") String code, @Param("courseId") Long courseId);

    @Insert("""
            insert into course (code, name, credit, category)
            values (#{code}, #{name}, #{credit}, #{category})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertCourse(CourseInsertCommand command);

    @Update("""
            update course
            set code = #{code},
                name = #{name},
                credit = #{credit},
                category = #{category}
            where id = #{id}
            """)
    int updateCourse(CourseInsertCommand command);

    @Select("""
            select count(*)
            from course_offering
            where course_id = #{courseId}
            """)
    long countOfferingsByCourseId(@Param("courseId") Long courseId);

    @Delete("""
            delete from course
            where id = #{courseId}
            """)
    int deleteCourse(@Param("courseId") Long courseId);

    @Select("""
            select
              co.id as offering_id,
              c.id as course_id,
              c.code as course_code,
              c.name as course_name,
              c.credit as credit,
              c.category as category,
              co.teacher_name as teacher_name,
              co.term as term,
              co.capacity as capacity,
              (
                select count(*)
                from course_selection cs
                where cs.offering_id = co.id
              ) as selected_count,
              co.schedule_text as schedule_text,
              co.classroom as classroom,
              co.selection_start_at as selection_start_at,
              co.selection_end_at as selection_end_at
            from course_offering co
            join course c on c.id = co.course_id
            where (#{term} is null or co.term = #{term})
            order by co.term desc, c.code asc, co.id desc
            """)
    List<AdminCourseOfferingRow> findOfferings(@Param("term") String term);

    @Select("""
            select
              co.id as offering_id,
              c.id as course_id,
              c.code as course_code,
              c.name as course_name,
              c.credit as credit,
              c.category as category,
              co.teacher_name as teacher_name,
              co.term as term,
              co.capacity as capacity,
              (
                select count(*)
                from course_selection cs
                where cs.offering_id = co.id
              ) as selected_count,
              co.schedule_text as schedule_text,
              co.classroom as classroom,
              co.selection_start_at as selection_start_at,
              co.selection_end_at as selection_end_at
            from course_offering co
            join course c on c.id = co.course_id
            where co.id = #{offeringId}
            """)
    AdminCourseOfferingRow findOfferingById(@Param("offeringId") Long offeringId);

    @Select("""
            select count(*)
            from sys_user u
            join sys_user_role ur on ur.user_id = u.id
            join sys_role r on r.id = ur.role_id
            where r.code = 'TEACHER'
              and u.display_name = #{teacherName}
              and u.status = 'ACTIVE'
            """)
    int countActiveTeacherByName(@Param("teacherName") String teacherName);

    @Select("""
            select count(*)
            from sys_role
            where code = 'TEACHER'
            """)
    int countTeacherRoleDefinitions();

    @Select("""
            select count(*)
            from sys_user
            where display_name = #{teacherName}
              and status = 'ACTIVE'
            """)
    int countActiveUserByDisplayName(@Param("teacherName") String teacherName);

    @Select("""
            select count(*)
            from course_offering
            where teacher_name = #{teacherName}
              and term = #{term}
              and schedule_text = #{scheduleText}
              and (#{exceptOfferingId} is null or id <> #{exceptOfferingId})
            """)
    int countTeacherScheduleConflicts(@Param("teacherName") String teacherName,
                                      @Param("term") String term,
                                      @Param("scheduleText") String scheduleText,
                                      @Param("exceptOfferingId") Long exceptOfferingId);

    @Select("""
            select count(*)
            from course_offering
            where classroom = #{classroom}
              and term = #{term}
              and schedule_text = #{scheduleText}
              and (#{exceptOfferingId} is null or id <> #{exceptOfferingId})
            """)
    int countClassroomScheduleConflicts(@Param("classroom") String classroom,
                                        @Param("term") String term,
                                        @Param("scheduleText") String scheduleText,
                                        @Param("exceptOfferingId") Long exceptOfferingId);

    @Insert("""
            insert into course_offering (
              course_id,
              teacher_name,
              term,
              capacity,
              schedule_text,
              classroom,
              selection_start_at,
              selection_end_at
            )
            values (
              #{courseId},
              #{teacherName},
              #{term},
              #{capacity},
              #{scheduleText},
              #{classroom},
              #{selectionStartAt},
              #{selectionEndAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertOffering(CourseOfferingCommand command);

    @Update("""
            update course_offering
            set
              course_id = #{courseId},
              teacher_name = #{teacherName},
              term = #{term},
              capacity = #{capacity},
              schedule_text = #{scheduleText},
              classroom = #{classroom},
              selection_start_at = #{selectionStartAt},
              selection_end_at = #{selectionEndAt}
            where id = #{id}
            """)
    int updateOffering(CourseOfferingCommand command);

    @Update("""
            update course_offering
            set selection_start_at = #{selectionStartAt},
                selection_end_at = #{selectionEndAt}
            where id = #{offeringId}
            """)
    int updateOfferingSelectionWindow(@Param("offeringId") Long offeringId,
                                      @Param("selectionStartAt") Instant selectionStartAt,
                                      @Param("selectionEndAt") Instant selectionEndAt);

    @Select("""
            select count(*)
            from course_selection
            where offering_id = #{offeringId}
            """)
    long countSelections(@Param("offeringId") Long offeringId);

    @Select("""
            select
              s.id as student_id,
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
            order by s.student_no asc
            """)
    List<OfferingStudentRow> findOfferingStudents(@Param("offeringId") Long offeringId);

    @Select("""
            select u.display_name
            from sys_user u
            join sys_user_role ur on ur.user_id = u.id
            join sys_role r on r.id = ur.role_id
            where r.code = 'TEACHER'
              and u.status = 'ACTIVE'
            order by u.display_name asc
            """)
    List<String> findTeacherOptions();

    @Select("""
            select room
            from classroom
            order by room asc
            """)
    List<String> findClassroomOptions();

    @Select("""
            select distinct term
            from course_offering
            order by term desc
            """)
    List<String> findTermOptions();

    @Delete("""
            delete from course_offering
            where id = #{offeringId}
            """)
    int deleteOffering(@Param("offeringId") Long offeringId);

    class CourseInsertCommand {
        private Long id;
        private String code;
        private String name;
        private Integer credit;
        private String category;

        public CourseInsertCommand(String code, String name, Integer credit, String category) {
            this.code = code;
            this.name = name;
            this.credit = credit;
            this.category = category;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setCredit(Integer credit) {
            this.credit = credit;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }

        public Integer getCredit() {
            return credit;
        }

        public String getCategory() {
            return category;
        }
    }

    class CourseOfferingCommand {
        private Long id;
        private final Long courseId;
        private final String teacherName;
        private final String term;
        private final Integer capacity;
        private final String scheduleText;
        private final String classroom;
        private final Instant selectionStartAt;
        private final Instant selectionEndAt;

        public CourseOfferingCommand(
                Long id,
                Long courseId,
                String teacherName,
                String term,
                Integer capacity,
                String scheduleText,
                String classroom,
                Instant selectionStartAt,
                Instant selectionEndAt
        ) {
            this.id = id;
            this.courseId = courseId;
            this.teacherName = teacherName;
            this.term = term;
            this.capacity = capacity;
            this.scheduleText = scheduleText;
            this.classroom = classroom;
            this.selectionStartAt = selectionStartAt;
            this.selectionEndAt = selectionEndAt;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getCourseId() {
            return courseId;
        }

        public String getTeacherName() {
            return teacherName;
        }

        public String getTerm() {
            return term;
        }

        public Integer getCapacity() {
            return capacity;
        }

        public String getScheduleText() {
            return scheduleText;
        }

        public String getClassroom() {
            return classroom;
        }

        public Instant getSelectionStartAt() {
            return selectionStartAt;
        }

        public Instant getSelectionEndAt() {
            return selectionEndAt;
        }
    }

    record OfferingStudentRow(Long studentId, String studentNo, String studentName, String className,
                              String major, Instant selectedAt, String gradeStatus) {
    }
}
