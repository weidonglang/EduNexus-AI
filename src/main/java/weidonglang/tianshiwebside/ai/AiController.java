package weidonglang.tianshiwebside.ai;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import weidonglang.tianshiwebside.common.api.ApiResponse;
import weidonglang.tianshiwebside.common.api.PageResponse;
import weidonglang.tianshiwebside.common.api.Pagination;
import weidonglang.tianshiwebside.common.trace.TraceIdHolder;

import org.springframework.jdbc.core.JdbcTemplate;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

@RestController
public class AiController {
    private final AiAssistantService assistantService;
    private final AiChatService chatService;
    private final NaturalSqlService naturalSqlService;
    private final SqlSchemaService sqlSchemaService;
    private final AiRemoteClient remoteClient;
    private final AiCallLogService callLogService;
    private final AcademicProfileService academicProfileService;
    private final LoadTestAnalysisService loadTestAnalysisService;
    private final JdbcTemplate jdbcTemplate;
    private final AiModelRegistryService modelRegistryService;
    private final AiSearchService searchService;
    private final AiChatSessionService chatSessionService;

    public AiController(
            AiAssistantService assistantService,
            AiChatService chatService,
            NaturalSqlService naturalSqlService,
            SqlSchemaService sqlSchemaService,
            AiRemoteClient remoteClient,
            AiCallLogService callLogService,
            AcademicProfileService academicProfileService,
            LoadTestAnalysisService loadTestAnalysisService,
            JdbcTemplate jdbcTemplate,
            AiModelRegistryService modelRegistryService,
            AiSearchService searchService,
            AiChatSessionService chatSessionService
    ) {
        this.assistantService = assistantService;
        this.chatService = chatService;
        this.naturalSqlService = naturalSqlService;
        this.sqlSchemaService = sqlSchemaService;
        this.remoteClient = remoteClient;
        this.callLogService = callLogService;
        this.academicProfileService = academicProfileService;
        this.loadTestAnalysisService = loadTestAnalysisService;
        this.jdbcTemplate = jdbcTemplate;
        this.modelRegistryService = modelRegistryService;
        this.searchService = searchService;
        this.chatSessionService = chatSessionService;
    }

    @PostMapping("/api/ai/assistant/ask")
    public ApiResponse<AiAssistantResponse> ask(@Valid @RequestBody AiAssistantRequest request, Principal principal) {
        return ApiResponse.success(assistantService.ask(request.question(), principal));
    }

    @PostMapping("/api/ai/chat")
    public ApiResponse<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request, Principal principal) {
        return ApiResponse.success(chatService.chat(request.message(), principal, request.modelId(), null, request.thinkingMode()));
    }

    @GetMapping("/api/ai/chat/models")
    public ApiResponse<List<AiModelRecord>> chatModels() {
        return ApiResponse.success(chatSessionService.chatModels());
    }

    @GetMapping("/api/ai/chat/sessions")
    public ApiResponse<List<AiChatSessionService.ChatSessionRow>> chatSessions(Principal principal) {
        return ApiResponse.success(chatSessionService.sessions(principal));
    }

    @PostMapping("/api/ai/chat/sessions")
    public ApiResponse<AiChatSessionService.ChatSessionRow> createChatSession(
            @RequestBody AiChatSessionService.ChatSessionRequest request,
            Principal principal
    ) {
        return ApiResponse.success(chatSessionService.create(principal, request));
    }

    @PutMapping("/api/ai/chat/sessions/{sessionId}")
    public ApiResponse<AiChatSessionService.ChatSessionRow> updateChatSession(
            @PathVariable Long sessionId,
            @RequestBody AiChatSessionService.ChatSessionRequest request,
            Principal principal
    ) {
        return ApiResponse.success(chatSessionService.update(principal, sessionId, request));
    }

    @DeleteMapping("/api/ai/chat/sessions/{sessionId}")
    public ApiResponse<Void> deleteChatSession(@PathVariable Long sessionId, Principal principal) {
        chatSessionService.delete(principal, sessionId);
        return ApiResponse.success();
    }

    @GetMapping("/api/ai/chat/sessions/{sessionId}/messages")
    public ApiResponse<List<AiChatSessionService.ChatMessageRow>> chatMessages(@PathVariable Long sessionId, Principal principal) {
        return ApiResponse.success(chatSessionService.messages(principal, sessionId));
    }

    @PostMapping("/api/ai/chat/sessions/{sessionId}/messages")
    public ApiResponse<AiChatSessionService.ChatSendResponse> sendChatMessage(
            @PathVariable Long sessionId,
            @RequestBody AiChatSessionService.ChatMessageRequest request,
            Principal principal
    ) {
        return ApiResponse.success(chatSessionService.send(principal, sessionId, request));
    }

    @GetMapping("/api/ai/status")
    public ApiResponse<AiServiceStatusResponse> status() {
        AiServiceStatusResponse status = remoteClient.status();
        AiSearchDtos.SearchConfig searchConfig = searchService.config();
        return ApiResponse.success(new AiServiceStatusResponse(
                status.aiServiceOnline(),
                status.ollamaEnabled(),
                status.ollamaReachable(),
                status.chatModel(),
                status.sqlModel(),
                status.currentMode(),
                status.lastLatencyMs(),
                status.lastError(),
                status.serviceName(),
                status.discoveryEnabled(),
                status.baseUrl(),
                modelRegistryService.defaultModelName("CHAT", status.chatModel()),
                modelRegistryService.defaultModelName("RAG", status.chatModel()),
                modelRegistryService.defaultModelName("SQL", status.sqlModel()),
                searchConfig.enabled(),
                searchConfig.provider(),
                searchConfig.lastStatus(),
                status.checkedAt()
        ));
    }

    @PostMapping("/api/ai/feedback")
    public ApiResponse<Void> feedback(@Valid @RequestBody AiFeedbackRequest request, Principal principal) {
        jdbcTemplate.update("""
                        insert into ai_feedback (call_log_id, username, rating, comment, trace_id, created_at)
                        values (?, ?, ?, ?, ?, ?)
                        """,
                request.callLogId(),
                principal == null ? "anonymous" : principal.getName(),
                request.rating(),
                request.comment(),
                TraceIdHolder.get(),
                Instant.now()
        );
        return ApiResponse.success();
    }

    @GetMapping("/api/ai/academic-profile")
    public ApiResponse<AcademicProfileResponse> academicProfile(Principal principal) {
        return ApiResponse.success(academicProfileService.currentProfile(principal));
    }

    @GetMapping("/api/admin/ai/sql/schema")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<SqlSchemaService.TableSchema>> schema() {
        return ApiResponse.success(sqlSchemaService.allowedSchemas());
    }

    @PostMapping("/api/admin/ai/sql/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<NaturalSqlGenerateResponse> generateSql(
            @Valid @RequestBody NaturalSqlGenerateRequest request,
            Principal principal
    ) {
        return ApiResponse.success(naturalSqlService.generate(request.question(), principal));
    }

    @PostMapping("/api/admin/ai/sql/execute")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<NaturalSqlExecuteResponse> executeSql(
            @Valid @RequestBody NaturalSqlExecuteRequest request,
            Principal principal
    ) {
        return ApiResponse.success(naturalSqlService.execute(request.sql(), principal));
    }

    @PostMapping("/api/admin/ai/load-test/analyze")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LoadTestAnalysisResponse> analyzeLoadTest(
            @Valid @RequestBody LoadTestAnalysisRequest request,
            Principal principal
    ) {
        return ApiResponse.success(loadTestAnalysisService.analyze(request.jsonName(), principal));
    }

    @GetMapping("/api/admin/ai/call-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<AiCallLogRow>> callLogs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String functionType,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Instant startAt,
            @RequestParam(required = false) Instant endAt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        int safePage = Pagination.safePage(page);
        int safeSize = Pagination.safeSize(size);
        AiCallLogService.AiCallLogQuery query = new AiCallLogService.AiCallLogQuery(
                keyword,
                username,
                functionType,
                success,
                level,
                startAt,
                endAt
        );
        return ApiResponse.success(new PageResponse<>(
                callLogService.logs(query, safeSize, Pagination.offset(safePage, safeSize)),
                safePage,
                safeSize,
                callLogService.countLogs(query)
        ));
    }

    public record AiFeedbackRequest(Long callLogId, String rating, String comment) {
    }
}
