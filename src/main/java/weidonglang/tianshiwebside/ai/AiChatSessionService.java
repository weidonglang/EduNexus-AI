package weidonglang.tianshiwebside.ai;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import weidonglang.tianshiwebside.common.error.BusinessException;
import weidonglang.tianshiwebside.common.error.ErrorCode;

import java.security.Principal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class AiChatSessionService {
    private final JdbcTemplate jdbcTemplate;
    private final AiModelRegistryService modelRegistryService;
    private final AiChatService chatService;

    public AiChatSessionService(
            JdbcTemplate jdbcTemplate,
            AiModelRegistryService modelRegistryService,
            AiChatService chatService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.modelRegistryService = modelRegistryService;
        this.chatService = chatService;
    }

    public List<AiModelRecord> chatModels() {
        return modelRegistryService.enabledModels("CHAT");
    }

    public List<ChatSessionRow> sessions(Principal principal) {
        String username = username(principal);
        return jdbcTemplate.query("""
                        select id, owner_username, title, model_id, model_name, created_at, updated_at
                        from ai_chat_session
                        where owner_username = ?
                          and deleted = false
                        order by updated_at desc, id desc
                        """,
                (rs, rowNum) -> new ChatSessionRow(
                        rs.getLong("id"),
                        rs.getString("title"),
                        nullableLong(rs.getObject("model_id")),
                        rs.getString("model_name"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                username);
    }

    @Transactional
    public ChatSessionRow create(Principal principal, ChatSessionRequest request) {
        String username = username(principal);
        AiModelRecord model = modelRegistryService.requireEnabledChatModel(request.modelId());
        String title = clean(request.title());
        if (title.isBlank()) {
            title = "新会话";
        }
        Instant now = Instant.now();
        jdbcTemplate.update("""
                        insert into ai_chat_session (owner_username, title, model_id, model_name, created_at, updated_at, deleted)
                        values (?, ?, ?, ?, ?, ?, false)
                        """,
                username, truncate(title, 160), model.id(), model.modelName(), now, now);
        Long id = jdbcTemplate.queryForObject("select max(id) from ai_chat_session where owner_username = ?", Long.class, username);
        return requireSession(username, id);
    }

    @Transactional
    public ChatSessionRow update(Principal principal, Long sessionId, ChatSessionRequest request) {
        String username = username(principal);
        requireSession(username, sessionId);
        AiModelRecord model = modelRegistryService.requireEnabledChatModel(request.modelId());
        String title = clean(request.title());
        jdbcTemplate.update("""
                        update ai_chat_session
                        set title = ?, model_id = ?, model_name = ?, updated_at = ?
                        where id = ? and owner_username = ? and deleted = false
                        """,
                title.isBlank() ? "新会话" : truncate(title, 160), model.id(), model.modelName(), Instant.now(), sessionId, username);
        return requireSession(username, sessionId);
    }

    @Transactional
    public void delete(Principal principal, Long sessionId) {
        String username = username(principal);
        requireSession(username, sessionId);
        jdbcTemplate.update("update ai_chat_session set deleted = true, updated_at = ? where id = ? and owner_username = ?",
                Instant.now(), sessionId, username);
    }

    public List<ChatMessageRow> messages(Principal principal, Long sessionId) {
        String username = username(principal);
        requireSession(username, sessionId);
        return jdbcTemplate.query("""
                        select id, session_id, role, content, service_mode, model_name, search_used, thinking_mode, created_at
                        from ai_chat_message
                        where session_id = ?
                        order by created_at asc, id asc
                        """,
                (rs, rowNum) -> new ChatMessageRow(
                        rs.getLong("id"),
                        rs.getLong("session_id"),
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getString("service_mode"),
                        rs.getString("model_name"),
                        rs.getBoolean("search_used"),
                        rs.getString("thinking_mode"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                sessionId);
    }

    @Transactional
    public ChatSendResponse send(Principal principal, Long sessionId, ChatMessageRequest request) {
        String username = username(principal);
        ChatSessionRow session = requireSession(username, sessionId);
        Long modelId = request.modelId() == null ? session.modelId() : request.modelId();
        AiModelRecord model = modelRegistryService.requireEnabledChatModel(modelId);
        Instant now = Instant.now();
        String userText = request.text();
        if (userText.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "消息内容不能为空");
        }
        String thinkingMode = normalizeThinkingMode(request.thinkingMode());
        jdbcTemplate.update("""
                        insert into ai_chat_message (session_id, role, content, service_mode, model_name, search_used, thinking_mode, created_at)
                        values (?, 'user', ?, null, ?, false, ?, ?)
                        """,
                sessionId, truncate(userText, 4000), model.modelName(), thinkingMode, now);
        AiChatResponse response = chatService.chat(userText, principal, model.id(), sessionId, thinkingMode);
        jdbcTemplate.update("""
                        insert into ai_chat_message (session_id, role, content, service_mode, model_name, search_used, thinking_mode, created_at)
                        values (?, 'assistant', ?, ?, ?, ?, ?, ?)
                        """,
                sessionId, truncate(response.answer(), 4000), response.serviceMode(), response.modelName(),
                response.searchUsed(), response.thinkingMode(), Instant.now());
        String nextTitle = session.title();
        if ("新会话".equals(nextTitle)) {
            nextTitle = truncate(userText.replaceAll("\\s+", " ").trim(), 30);
        }
        jdbcTemplate.update("""
                        update ai_chat_session
                        set title = ?, model_id = ?, model_name = ?, updated_at = ?
                        where id = ?
                        """,
                nextTitle, model.id(), model.modelName(), Instant.now(), sessionId);
        return new ChatSendResponse(response, messages(principal, sessionId), requireSession(username, sessionId));
    }

    private ChatSessionRow requireSession(String username, Long sessionId) {
        if (sessionId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "会话 ID 不能为空");
        }
        try {
            return jdbcTemplate.queryForObject("""
                            select id, owner_username, title, model_id, model_name, created_at, updated_at
                            from ai_chat_session
                            where id = ?
                              and owner_username = ?
                              and deleted = false
                            """,
                    (rs, rowNum) -> new ChatSessionRow(
                            rs.getLong("id"),
                            rs.getString("title"),
                            nullableLong(rs.getObject("model_id")),
                            rs.getString("model_name"),
                            instant(rs.getTimestamp("created_at")),
                            instant(rs.getTimestamp("updated_at"))
                    ),
                    sessionId, username);
        } catch (EmptyResultDataAccessException ex) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "AI 会话不存在或无权访问");
        }
    }

    private String username(Principal principal) {
        return principal == null ? "anonymous" : principal.getName();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private String normalizeThinkingMode(String mode) {
        String value = mode == null ? "AUTO" : mode.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (value) {
            case "ON", "OFF" -> value;
            default -> "AUTO";
        };
    }

    public record ChatSessionRow(Long id, String title, Long modelId, String modelName, Instant createdAt, Instant updatedAt) {
    }

    public record ChatMessageRow(Long id, Long sessionId, String role, String content, String serviceMode,
                                 String modelName, boolean searchUsed, String thinkingMode, Instant createdAt) {
    }

    public record ChatSessionRequest(String title, Long modelId) {
    }

    public record ChatMessageRequest(String message, String content, Long modelId, String thinkingMode) {
        String text() {
            return cleanValue(message).isBlank() ? cleanValue(content) : cleanValue(message);
        }

        private static String cleanValue(String value) {
            return value == null ? "" : value.trim();
        }
    }

    public record ChatSendResponse(AiChatResponse response, List<ChatMessageRow> messages, ChatSessionRow session) {
    }
}
