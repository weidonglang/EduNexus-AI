package weidonglang.tianshiwebside.course.mapper;

import org.apache.ibatis.annotations.*;

import java.time.Instant;

@Mapper
public interface CourseSelectionWriteMapper {
    /**
     * 功能：根据登录账号查询学生 ID。
     * 说明：抢课和退课最终写入的是 student_id，本方法把前端登录账号转换为数据库学生主键。
     */
    @Select("""
            select s.id
            from student s
            join sys_user u on u.id = s.user_id
            where u.username = #{username}
            """)
    Long findStudentIdByUsername(@Param("username") String username);

    /**
     * 功能：查询教学班详细信息。
     * 说明：抢课前需要读取教学班容量、选课开始结束时间、课程名称、教师和教室等数据，
     * 这些数据会被抢课服务短时间缓存，减少高并发下重复查库。
     */
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
              co.schedule_text as schedule_text,
              co.classroom as classroom,
              co.selection_start_at as selection_start_at,
              co.selection_end_at as selection_end_at
            from course_offering co
            join course c on c.id = co.course_id
            where co.id = #{offeringId}
            """)
    CourseOfferingDetailRow findOfferingDetail(@Param("offeringId") Long offeringId);

    /**
     * 功能：在数据库兜底抢课路径中锁定教学班行。
     * 说明：Redis 不可用时，同一教学班的并发抢课请求会在事务内串行执行容量检查和写入，
     * 避免多个请求同时看到相同剩余名额后一起插入造成超容量。
     */
    @Select("""
            select id
            from course_offering
            where id = #{offeringId}
            for update
            """)
    Long lockOfferingForUpdate(@Param("offeringId") Long offeringId);

    /**
     * 功能：校验学生是否已经选择过指定教学班。
     * 说明：用于防止同一学生重复选择同一教学班，是 Redis 幂等之外的数据库业务校验。
     */
    @Select("""
            select count(*)
            from course_selection
            where student_id = #{studentId}
              and offering_id = #{offeringId}
            """)
    long countStudentOfferingSelection(@Param("studentId") Long studentId, @Param("offeringId") Long offeringId);

    @Select("""
            select count(*)
            from course_selection cs
            join course_offering selected_offering on selected_offering.id = cs.offering_id
            join course_offering target_offering on target_offering.id = #{offeringId}
            where cs.student_id = #{studentId}
              and selected_offering.term = target_offering.term
              and selected_offering.schedule_text = target_offering.schedule_text
            """)
    long countStudentScheduleConflicts(@Param("studentId") Long studentId, @Param("offeringId") Long offeringId);

    /**
     * 功能：统计教学班当前已选人数。
     * 说明：用于计算剩余名额、预热 Redis 库存，以及 Redis 不可用时数据库兜底判断是否满员。
     */
    @Select("""
            select count(*)
            from course_selection
            where offering_id = #{offeringId}
            """)
    long countOfferingSelections(@Param("offeringId") Long offeringId);

    @Select("""
            select offering_id
            from course_selection
            where id = #{selectionId}
              and student_id = #{studentId}
            """)
    Long findOfferingIdBySelection(@Param("selectionId") Long selectionId, @Param("studentId") Long studentId);

    /**
     * 功能：新增学生选课记录。
     * 说明：Redis 或数据库容量校验通过后，将 student_id、offering_id 和选课时间写入 course_selection。
     */
    @Insert("""
            insert into course_selection (student_id, offering_id, selected_at)
            values (#{studentId}, #{offeringId}, #{selectedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "selectionId")
    int insertSelection(InsertCourseSelectionCommand command);

    /**
     * 功能：按登录账号新增选课记录。
     * 说明：Redis 抢课路径中已经先扣减库存，为减少一次 student_id 查询，
     * 直接通过 username 关联 student 表完成选课记录插入。
     */
    @Insert("""
            insert into course_selection (student_id, offering_id, selected_at)
            select s.id, #{offeringId}, #{selectedAt}
            from student s
            join sys_user u on u.id = s.user_id
            where u.username = #{username}
            """)
    @Options(useGeneratedKeys = true, keyProperty = "selectionId")
    int insertSelectionByUsername(InsertCourseSelectionByUsernameCommand command);

    /**
     * 功能：删除学生选课记录。
     * 说明：退课时同时限定 selection_id 和 student_id，避免学生删除别人的选课数据。
     */
    @Delete("""
            delete from course_selection
            where id = #{selectionId}
              and student_id = #{studentId}
            """)
    int deleteSelection(@Param("selectionId") Long selectionId, @Param("studentId") Long studentId);

    /**
     * 功能：清理压测产生的选课记录。
     * 说明：根据教学班、压测开始时间和压测账号范围删除本次压测数据，
     * 方便多次演示后保持数据库干净。
     */
    @Delete("""
            <script>
            delete from course_selection
            where offering_id = #{offeringId}
              and selected_at &gt;= #{selectedAfter}
              and student_id in (
                select s.id
                from student s
                join sys_user u on u.id = s.user_id
                where u.username in
              <foreach collection="usernames" item="username" open="(" separator="," close=")">
                #{username}
              </foreach>
              )
            </script>
            """)
    int deleteLoadTestSelections(
            @Param("offeringId") Long offeringId,
            @Param("selectedAfter") Instant selectedAfter,
            @Param("usernames") java.util.List<String> usernames
    );

    final class InsertCourseSelectionCommand {
        private final Long studentId;
        private final Long offeringId;
        private final Instant selectedAt;
        private Long selectionId;

        public InsertCourseSelectionCommand(Long studentId, Long offeringId, Instant selectedAt) {
            this.studentId = studentId;
            this.offeringId = offeringId;
            this.selectedAt = selectedAt;
        }

        public Long getStudentId() {
            return studentId;
        }

        public Long getOfferingId() {
            return offeringId;
        }

        public Instant getSelectedAt() {
            return selectedAt;
        }

        public Long getSelectionId() {
            return selectionId;
        }

        public void setSelectionId(Long selectionId) {
            this.selectionId = selectionId;
        }
    }

    final class InsertCourseSelectionByUsernameCommand {
        private final String username;
        private final Long offeringId;
        private final Instant selectedAt;
        private Long selectionId;

        public InsertCourseSelectionByUsernameCommand(String username, Long offeringId, Instant selectedAt) {
            this.username = username;
            this.offeringId = offeringId;
            this.selectedAt = selectedAt;
        }

        public String getUsername() {
            return username;
        }

        public Long getOfferingId() {
            return offeringId;
        }

        public Instant getSelectedAt() {
            return selectedAt;
        }

        public Long getSelectionId() {
            return selectionId;
        }

        public void setSelectionId(Long selectionId) {
            this.selectionId = selectionId;
        }
    }
}
