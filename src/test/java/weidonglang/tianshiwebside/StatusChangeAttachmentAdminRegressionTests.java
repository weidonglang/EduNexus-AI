package weidonglang.tianshiwebside;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StatusChangeAttachmentAdminRegressionTests extends HttpRegressionTestSupport {
    @Test
    void adminCanListPreviewAndDownloadStatusChangeAttachmentsWithRoleBoundaries() throws Exception {
        String suffix = suffix();
        String admin = "v14_file_admin_" + suffix;
        String teacher = "v14_file_teacher_" + suffix;
        String owner = "v14_file_owner_" + suffix;
        String otherStudent = "v14_file_other_" + suffix;
        seedUser(admin, "附件管理员", List.of("ADMIN"), List.of());
        seedUser(teacher, "附件教师", List.of("TEACHER"), List.of());
        seedStudent(owner, "附件学生");
        seedStudent(otherStudent, "其他学生");

        String ownerToken = login(owner);
        JsonNode application = json(post("/api/students/me/status-changes", ownerToken, """
                {"type":"OTHER","reason":"上传证明材料"}
                """), HttpStatus.OK);
        long applicationId = application.at("/data/id").asLong();

        Path uploadDir = Path.of("target", "test-uploads").toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
        Path pdf = uploadDir.resolve("v14-" + suffix + ".pdf");
        Files.writeString(pdf, "%PDF-1.4\nv14 attachment\n");
        jdbcTemplate.update("""
                        insert into status_change_attachment
                          (application_id, original_filename, stored_path, content_type, size_bytes, uploaded_at)
                        values (?, ?, ?, ?, ?, ?)
                        """,
                applicationId, "证明材料.pdf", pdf.toString(), "application/pdf", Files.size(pdf), Instant.now());
        Long attachmentId = jdbcTemplate.queryForObject("""
                select max(id)
                from status_change_attachment
                where application_id = ?
                """, Long.class, applicationId);

        String adminToken = login(admin);
        JsonNode attachments = json(get("/api/admin/status-changes/" + applicationId + "/attachments", adminToken), HttpStatus.OK);
        assertThat(attachments.at("/data/0/originalFilename").asText()).isEqualTo("证明材料.pdf");
        assertThat(attachments.at("/data/0/fileTypeLabel").asText()).isEqualTo("PDF");
        assertThat(attachments.at("/data/0/previewable").asBoolean()).isTrue();

        assertThat(get("/api/admin/status-changes/" + applicationId + "/attachments/" + attachmentId + "/download", adminToken).statusCode())
                .isEqualTo(HttpStatus.OK.value());
        assertThat(get("/api/admin/status-changes/" + applicationId + "/attachments/" + attachmentId + "/preview", adminToken).headers()
                .firstValue("content-disposition").orElse("")).contains("inline");

        assertThat(get("/api/students/me/status-changes/" + applicationId + "/attachments/" + attachmentId + "/download", login(otherStudent)).statusCode())
                .isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(get("/api/admin/status-changes/" + applicationId + "/attachments", login(teacher)).statusCode())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
    }
}
