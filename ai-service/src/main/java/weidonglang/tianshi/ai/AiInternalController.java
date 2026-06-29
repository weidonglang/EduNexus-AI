package weidonglang.tianshi.ai;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.stream.Collectors;

@RestController
public class AiInternalController {
    private final OllamaClient ollamaClient;
    private final String chatModel;
    private final String sqlModel;

    public AiInternalController(
            OllamaClient ollamaClient,
            @Value("${ollama.chat-model:qwen3:8b}") String chatModel,
            @Value("${ollama.sql-model:qwen2.5-coder:7b}") String sqlModel
    ) {
        this.ollamaClient = ollamaClient;
        this.chatModel = chatModel;
        this.sqlModel = sqlModel;
    }

    @PostMapping("/internal/ai/rag/answer")
    public AiDtos.RagAnswerResponse answer(@Valid @RequestBody AiDtos.RagAnswerRequest request) {
        List<AiDtos.SourceDocument> sources = sortedSources(request.documents());
        String prompt = ragPrompt(request.question(), sources);
        String answer = ollamaClient.generate(chatModel, prompt)
                .orElseGet(() -> fallbackAnswer(request.question(), sources));
        String mode = answer.startsWith("根据检索资料") ? "ai-service-fallback" : "ollama:" + chatModel;
        return new AiDtos.RagAnswerResponse(answer, sources, mode, "ANSWER", null);
    }

    @PostMapping("/internal/ai/chat")
    public AiDtos.ChatResponse chat(@Valid @RequestBody AiDtos.ChatRequest request) {
        String selectedModel = cleanModelName(request.modelName());
        if (selectedModel.isBlank()) {
            selectedModel = chatModel;
        }
        String thinkingMode = normalizeThinkingMode(request.thinkingMode());
        String prompt = """
                你是“教学综合信息服务平台”的 AI 聊天助手。请用中文回答。
                如果用户说“咱们系统”“这个系统”“项目”，默认指教学综合信息服务平台。
                你可以介绍系统定位、用户角色、核心业务、AI 功能、Redis 抢课和压测能力。
                注意：如果用户询问具体教务规则、个人成绩、选课资格、考试安排、申请审核结论等正式业务问题，应提醒其使用智能教务助手或对应业务页面，因为聊天答案不作为正式教务依据。

                系统背景：
                %s

                用户消息：
                %s
                """.formatted(systemOverview(), applyThinkingMode(request.message(), thinkingMode, selectedModel));
        var generated = ollamaClient.generate(selectedModel, prompt);
        String actualModel = selectedModel;
        String fallbackReason = "";
        if (generated.isEmpty() && !selectedModel.equals(chatModel)) {
            fallbackReason = "selected model unavailable; " + ollamaClient.lastError();
            generated = ollamaClient.generate(chatModel, prompt);
            actualModel = chatModel;
        }
        boolean fallback = generated.isEmpty();
        String answer = generated.orElseGet(() -> fallbackChatAnswer(request.message()));
        if (fallback && fallbackReason.isBlank()) {
            fallbackReason = ollamaClient.enabled()
                    ? "ollama generation failed; " + ollamaClient.lastError()
                    : "ollama disabled";
        }
        String mode = fallback ? "ai-service-fallback" : "ollama:" + actualModel;
        return new AiDtos.ChatResponse(
                answer,
                mode,
                actualModel,
                false,
                List.of(),
                "ai-service 不直接执行联网搜索",
                request.modelId(),
                selectedModel,
                actualModel,
                fallback || !actualModel.equals(selectedModel),
                fallbackReason.isBlank() ? null : fallbackReason,
                thinkingMode
        );
    }

    @GetMapping("/internal/ai/status")
    public AiDtos.StatusResponse status() {
        return new AiDtos.StatusResponse(
                ollamaClient.enabled(),
                ollamaClient.reachable(),
                chatModel,
                sqlModel,
                ollamaClient.lastError()
        );
    }

    @PostMapping("/internal/ai/load-test/analyze")
    public AiDtos.LoadTestAnalysisResponse analyzeLoadTest(@RequestBody AiDtos.LoadTestAnalysisRequest request) {
        String prompt = """
                你是系统压测报告分析助手。请根据 JSON 报告输出：
                1. 本次压测结论
                2. 可能瓶颈
                3. 优化建议
                4. 风险等级
                用中文，简洁分点。

                报告：
                %s
                """.formatted(String.valueOf(request.report()));
        String answer = ollamaClient.generate(chatModel, prompt).orElse("");
        if (answer.isBlank()) {
            return fallbackLoadTestAnalysis();
        }
        return new AiDtos.LoadTestAnalysisResponse(
                answer,
                List.of("由模型根据压测 JSON 判断，需结合 Redis 监控和数据库容量核对。"),
                List.of("检查 Redis 可用性、P95 延迟、数据库落库数量和是否超卖。"),
                "需人工复核",
                "ollama:" + chatModel
        );
    }

    @PostMapping("/internal/ai/sql/generate")
    public AiDtos.SqlGenerateResponse generateSql(@Valid @RequestBody AiDtos.SqlGenerateRequest request) {
        String prompt = sqlPrompt(request.question(), request.schemas());
        String raw = ollamaClient.generate(sqlModel, prompt).orElse("");
        String sql = extractSql(raw);
        if (sql.isBlank()) {
            AiDtos.SqlGenerateResponse fallback = fallbackSql(request.question());
            return new AiDtos.SqlGenerateResponse(fallback.sql(), fallback.explanation(), fallback.warnings(), "ai-service-fallback");
        }
        return new AiDtos.SqlGenerateResponse(
                sql,
                "由本地模型根据白名单表结构生成，主系统仍会二次安全校验。",
                List.of("模型输出仅作为草稿，执行前必须经过主系统 SQL 安全校验。"),
                "ollama:" + sqlModel
        );
    }

    private List<AiDtos.SourceDocument> sortedSources(List<AiDtos.SourceDocument> documents) {
        if (documents == null) {
            return List.of();
        }
        return documents.stream()
                .sorted(Comparator.comparingDouble(AiDtos.SourceDocument::score).reversed())
                .limit(8)
                .toList();
    }

    private String systemOverview() {
        return """
                教学综合信息服务平台是一个面向高校教务场景的综合管理系统，用于演示学生、教师、管理员三类角色之间的业务互通。
                主要角色：
                1. 学生：查看个人信息、课表、成绩、考试、通知，进行选课、抢课、教学评价、学籍异动申请、报名申请、查看学业画像。
                2. 教师：查看任课课程、教学班学生、录入或维护成绩、查看考试安排和教学评价结果。
                3. 管理员：维护用户与角色权限、课程和教学班、成绩、考试、通知公告、文件、审核申请、查看审计日志、数据库只读浏览、Redis 状态和压测报告。
                核心业务：
                1. 课程与教学班管理，管理员新增课程后教师端可看到自己的任课课程。
                2. 学生选课和抢课，学生选课后教师端可看到对应教学班学生名单，系统通过容量约束、行锁和 Redis 库存保护降低超卖风险。
                3. 成绩、考试、课表、教学评价、学籍异动、报名申请、通知公告、文件管理等教务闭环。
                AI 功能：
                1. 智能教务助手：基于 RAG 检索教务规则、通知公告、教学计划、学生学业概况，并展示参考依据。
                2. 无法回答机制：知识不足、权限外个人数据、非教务范围问题会拒答或建议人工确认。
                3. 自然语言只读查库：管理员用自然语言生成 SELECT SQL，系统做只读、安全字段、单语句和白名单校验。
                4. AI 服务状态检测：展示 ai-service、Ollama、模型、模式、耗时和错误信息。
                5. AI 调用日志：记录 RAG、SQL、聊天、压测解读等调用历史。
                6. 学业画像：展示已修学分、未完成学分、挂科、重修、毕业风险和 AI 建议。
                7. AI 压测报告解读：分析抢课压测请求数、成功数、失败数、QPS、延迟、Redis 库存和落库情况。
                8. 通用 AI 聊天：用于答辩准备、系统介绍、文本润色和普通问答。
                技术特点：
                主系统使用 Spring Boot、MyBatis/JPA、Flyway、MySQL、Redis、Vue 3、Vite、Element Plus；AI 服务独立为 ai-service，通过 Ollama 调用 qwen3:8b 和 qwen2.5-coder:7b，并支持本地兜底。
                """;
    }

    private String fallbackChatAnswer(String message) {
        String normalized = message == null ? "" : message.trim();
        if (normalized.contains("系统") || normalized.contains("项目") || normalized.contains("介绍")) {
            return """
                    AI 聊天当前处于本地兜底模式。教学综合信息服务平台是一个高校教务综合管理系统，覆盖学生、教师、管理员三类角色。
                    学生端支持个人信息、课表、成绩、考试、选课抢课、教学评价、学籍异动、报名申请和学业画像。
                    教师端支持查看任课课程、教学班学生、成绩录入、考试安排和评价结果。
                    管理端支持用户权限、课程教学班、成绩考试、通知文件、申请审核、审计日志、数据库只读浏览、Redis 监控和压测报告。
                    AI 部分包括 RAG 教务助手、自然语言只读查库、AI 服务状态、AI 调用日志、学业画像、压测报告解读和通用聊天。
                    Redis 抢课模块通过库存缓存、幂等保护、短锁、数据库容量约束和行锁保护降低高并发超卖风险。
                    """;
        }
        return "AI 聊天当前处于本地兜底模式。涉及正式教务问题时，请使用智能教务助手并查看引用来源；如果需要介绍系统，可以问“介绍一下咱们系统”。";
    }

    private String cleanModelName(String modelName) {
        return modelName == null ? "" : modelName.trim();
    }

    String applyThinkingMode(String userMessage, String mode, String modelName) {
        String message = userMessage == null ? "" : userMessage;
        String normalized = normalizeThinkingMode(mode);
        if ("AUTO".equals(normalized) || hasExplicitThinkingDirective(message) || !supportsThinkingMode(modelName)) {
            return message;
        }
        return switch (normalized) {
            case "ON" -> "/think\n" + message;
            case "OFF" -> "/no_think\n" + message;
            default -> message;
        };
    }

    private boolean hasExplicitThinkingDirective(String message) {
        String trimmed = message == null ? "" : message.stripLeading().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("/think") || trimmed.startsWith("/no_think");
    }

    private boolean supportsThinkingMode(String modelName) {
        String normalized = modelName == null ? "" : modelName.toLowerCase(Locale.ROOT);
        return normalized.contains("qwen3") || normalized.contains("deepseek-r1") || normalized.contains("reason");
    }

    private String normalizeThinkingMode(String mode) {
        String normalized = mode == null ? "AUTO" : mode.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ON", "OFF" -> normalized;
            default -> "AUTO";
        };
    }

    private String ragPrompt(String question, List<AiDtos.SourceDocument> sources) {
        String context = sources.stream()
                .map(source -> "[" + source.id() + "] " + source.title() + "\n" + source.content())
                .collect(Collectors.joining("\n\n"));
        return """
                你是高校教务系统的智能助手。只根据给定资料回答，不要编造。
                回答要求：
                1. 用中文。
                2. 结论明确，必要时分点。
                3. 每个关键结论后标注来源编号，例如 [notice:1]。
                4. 如果资料不足，明确说明需要人工确认。

                用户问题：
                %s

                检索资料：
                %s
                """.formatted(question, context);
    }

    private String sqlPrompt(String question, List<AiDtos.TableSchema> schemas) {
        String schemaText = schemas == null ? "" : schemas.stream()
                .map(table -> table.tableName() + "(" + table.columns().stream()
                        .map(column -> column.columnName() + ":" + column.dataType())
                        .collect(Collectors.joining(", ")) + ")")
                .collect(Collectors.joining("\n"));
        return """
                你是只读 SQL 生成器。根据问题和白名单表结构生成一条 MySQL SELECT。
                严格要求：
                1. 只能输出一条 SELECT SQL，不要解释。
                2. 禁止 INSERT、UPDATE、DELETE、DROP、ALTER、TRUNCATE。
                3. 禁止多语句。
                4. 必须使用白名单中的表和字段。
                5. 必须带 limit，最大 100。

                用户问题：
                %s

                白名单表结构：
                %s
                """.formatted(question, schemaText);
    }

    private String fallbackAnswer(String question, List<AiDtos.SourceDocument> sources) {
        if (sources.isEmpty()) {
            return "根据检索资料，暂时没有找到可引用内容。请补充公告、规则或教学计划后再提问。";
        }
        StringBuilder builder = new StringBuilder("根据检索资料，可以先这样判断：\n");
        for (int i = 0; i < Math.min(3, sources.size()); i++) {
            AiDtos.SourceDocument source = sources.get(i);
            builder.append(i + 1)
                    .append(". ")
                    .append(source.title())
                    .append("：")
                    .append(source.content())
                    .append(" [")
                    .append(source.id())
                    .append("]\n");
        }
        builder.append("这个回答来自 ai-service 离线兜底；开启 Ollama 后会生成更完整的自然语言答案。");
        return builder.toString();
    }

    private AiDtos.SqlGenerateResponse fallbackSql(String question) {
        String q = question == null ? "" : question;
        if (q.contains("挂科") || q.contains("不及格")) {
            return new AiDtos.SqlGenerateResponse("""
                    select c.name as course_name, count(*) as failed_count
                    from academic_grade ag
                    join course c on c.id = ag.course_id
                    where ag.score < 60
                    group by c.id, c.name
                    order by failed_count desc
                    limit 10
                    """.strip(), "兜底生成挂科课程排行。", List.of("未启用 Ollama，使用 ai-service 内置模板。"), "ai-service-fallback");
        }
        return new AiDtos.SqlGenerateResponse("""
                select c.name as course_name, co.teacher_name, co.term, co.capacity,
                       count(cs.id) as selected_count
                from course_offering co
                join course c on c.id = co.course_id
                left join course_selection cs on cs.offering_id = co.id
                group by co.id, c.name, co.teacher_name, co.term, co.capacity
                order by selected_count desc
                limit 10
                """.strip(), "兜底生成选课人数排行。", List.of("未启用 Ollama，使用 ai-service 内置模板。"), "ai-service-fallback");
    }

    private AiDtos.LoadTestAnalysisResponse fallbackLoadTestAnalysis() {
        List<String> bottlenecks = new ArrayList<>();
        bottlenecks.add("未启用 Ollama，ai-service 只能给出通用压测检查建议。");
        return new AiDtos.LoadTestAnalysisResponse(
                "压测报告已收到，但当前 AI 模型不可用，已切换为通用兜底分析。",
                bottlenecks,
                List.of("重点核对成功数是否超过教学班容量。", "检查 Redis 是否在线以及库存是否与数据库剩余容量一致。", "关注平均响应时间和 P95 是否明显升高。"),
                "需人工复核",
                "ai-service-fallback"
        );
    }

    private String extractSql(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replace("```sql", "```").replace("```SQL", "```").trim();
        if (cleaned.contains("```")) {
            String[] parts = cleaned.split("```");
            for (String part : parts) {
                if (part.toLowerCase(Locale.ROOT).contains("select ")) {
                    return normalizeSql(part);
                }
            }
        }
        int index = cleaned.toLowerCase(Locale.ROOT).indexOf("select ");
        if (index < 0) {
            return "";
        }
        return normalizeSql(cleaned.substring(index));
    }

    private String normalizeSql(String sql) {
        String normalized = sql.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)--.*?$", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
