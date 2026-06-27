package weidonglang.tianshiwebside.file;

import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import weidonglang.tianshiwebside.common.api.ApiResponse;
import weidonglang.tianshiwebside.common.error.BusinessException;
import weidonglang.tianshiwebside.common.error.ErrorCode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin/status-changes/{applicationId}/attachments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStatusChangeAttachmentController {
    private final StatusChangeAttachmentMapper mapper;

    public AdminStatusChangeAttachmentController(StatusChangeAttachmentMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public ApiResponse<List<AdminAttachmentDto>> list(@PathVariable Long applicationId) {
        return ApiResponse.success(mapper.findByApplicationId(applicationId).stream()
                .map(this::toDto)
                .toList());
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long applicationId, @PathVariable Long attachmentId) {
        StatusChangeAttachmentMapper.AttachmentRow row = requireAttachment(applicationId, attachmentId);
        return fileResponse(row, false);
    }

    @GetMapping("/{attachmentId}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long applicationId, @PathVariable Long attachmentId) {
        StatusChangeAttachmentMapper.AttachmentRow row = requireAttachment(applicationId, attachmentId);
        return fileResponse(row, isPreviewable(row.contentType()));
    }

    private ResponseEntity<Resource> fileResponse(StatusChangeAttachmentMapper.AttachmentRow row, boolean inline) {
        Path path = Paths.get(row.storedPath()).toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在或已被移动");
        }
        String encodedName = URLEncoder.encode(row.originalFilename(), StandardCharsets.UTF_8).replace("+", "%20");
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (row.contentType() != null && !row.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(row.contentType());
        }
        String disposition = (inline ? "inline" : "attachment") + "; filename*=UTF-8''" + encodedName;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(new PathResource(path));
    }

    private StatusChangeAttachmentMapper.AttachmentRow requireAttachment(Long applicationId, Long attachmentId) {
        StatusChangeAttachmentMapper.AttachmentRow row = mapper.findByApplicationAndId(applicationId, attachmentId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        return row;
    }

    private AdminAttachmentDto toDto(StatusChangeAttachmentMapper.AttachmentRow row) {
        return new AdminAttachmentDto(
                row.id(),
                row.applicationId(),
                row.originalFilename(),
                row.contentType(),
                fileTypeLabel(row.contentType()),
                row.sizeBytes(),
                row.uploadedAt(),
                isPreviewable(row.contentType())
        );
    }

    private boolean isPreviewable(String contentType) {
        return "application/pdf".equals(contentType)
                || "image/jpeg".equals(contentType)
                || "image/png".equals(contentType);
    }

    private String fileTypeLabel(String contentType) {
        if ("application/pdf".equals(contentType)) return "PDF";
        if ("image/jpeg".equals(contentType) || "image/png".equals(contentType)) return "图片";
        if ("application/msword".equals(contentType)
                || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType)) {
            return "Word 文档";
        }
        if ("application/vnd.ms-excel".equals(contentType)
                || "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(contentType)) {
            return "Excel 表格";
        }
        return "其他文件";
    }

    public record AdminAttachmentDto(
            Long id,
            Long applicationId,
            String originalFilename,
            String contentType,
            String fileTypeLabel,
            Long sizeBytes,
            Instant uploadedAt,
            boolean previewable
    ) {
    }
}
