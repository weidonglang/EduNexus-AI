package weidonglang.tianshiwebside.file;

import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.List;

@Mapper
public interface StatusChangeAttachmentMapper {
    @Select("""
            select count(*)
            from student_status_change_application a
            join student s on s.id = a.student_id
            join sys_user u on u.id = s.user_id
            where a.id = #{applicationId}
              and u.username = #{username}
            """)
    int countOwnedApplication(@Param("applicationId") Long applicationId, @Param("username") String username);

    @Insert("""
            insert into status_change_attachment
              (application_id, original_filename, stored_path, content_type, size_bytes, uploaded_at)
            values
              (#{applicationId}, #{originalFilename}, #{storedPath}, #{contentType}, #{sizeBytes}, #{uploadedAt})
            """)
    int insertAttachment(@Param("applicationId") Long applicationId, @Param("originalFilename") String originalFilename,
                         @Param("storedPath") String storedPath, @Param("contentType") String contentType,
                         @Param("sizeBytes") long sizeBytes, @Param("uploadedAt") Instant uploadedAt);

    @Select("""
            select id, application_id as application_id, original_filename as original_filename,
                   stored_path as stored_path, content_type as content_type, size_bytes as size_bytes,
                   uploaded_at as uploaded_at
            from status_change_attachment
            where application_id = #{applicationId}
            order by uploaded_at desc
            """)
    List<AttachmentRow> findByApplicationId(@Param("applicationId") Long applicationId);

    @Select("""
            select
              att.id,
              att.application_id as application_id,
              att.original_filename as original_filename,
              att.stored_path as stored_path,
              att.content_type as content_type,
              att.size_bytes as size_bytes,
              att.uploaded_at as uploaded_at,
              s.student_no as student_no,
              u.display_name as student_name,
              a.type as change_type,
              a.status as application_status
            from status_change_attachment att
            join student_status_change_application a on a.id = att.application_id
            join student s on s.id = a.student_id
            join sys_user u on u.id = s.user_id
            order by att.uploaded_at desc
            """)
    List<AdminAttachmentRow> findAdminAttachments();

    @Select("""
            select id, application_id as application_id, original_filename as original_filename,
                   stored_path as stored_path, content_type as content_type, size_bytes as size_bytes,
                   uploaded_at as uploaded_at
            from status_change_attachment
            where id = #{attachmentId}
            """)
    AttachmentRow findById(@Param("attachmentId") Long attachmentId);

    @Select("""
            select id, application_id as application_id, original_filename as original_filename,
                   stored_path as stored_path, content_type as content_type, size_bytes as size_bytes,
                   uploaded_at as uploaded_at
            from status_change_attachment
            where application_id = #{applicationId}
              and id = #{attachmentId}
            """)
    AttachmentRow findByApplicationAndId(@Param("applicationId") Long applicationId, @Param("attachmentId") Long attachmentId);

    @Select("""
            select att.id, att.application_id as application_id, att.original_filename as original_filename,
                   att.stored_path as stored_path, att.content_type as content_type, att.size_bytes as size_bytes,
                   att.uploaded_at as uploaded_at
            from status_change_attachment att
            join student_status_change_application a on a.id = att.application_id
            join student s on s.id = a.student_id
            join sys_user u on u.id = s.user_id
            where att.id = #{attachmentId}
              and att.application_id = #{applicationId}
              and u.username = #{username}
            """)
    AttachmentRow findOwnedById(@Param("applicationId") Long applicationId, @Param("attachmentId") Long attachmentId,
                                @Param("username") String username);

    @Delete("""
            delete from status_change_attachment
            where id = #{attachmentId}
            """)
    int deleteById(@Param("attachmentId") Long attachmentId);

    record AttachmentRow(Long id, Long applicationId, String originalFilename, String storedPath,
                         String contentType, Long sizeBytes, Instant uploadedAt) {
    }

    record AdminAttachmentRow(Long id, Long applicationId, String originalFilename, String storedPath,
                              String contentType, Long sizeBytes, Instant uploadedAt, String studentNo,
                              String studentName, String changeType, String applicationStatus) {
    }
}
