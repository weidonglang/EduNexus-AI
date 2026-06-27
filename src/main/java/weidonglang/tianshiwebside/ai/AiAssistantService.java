package weidonglang.tianshiwebside.ai;

import org.springframework.stereotype.Service;
import weidonglang.tianshiwebside.common.trace.TraceIdHolder;
import weidonglang.tianshiwebside.governance.ContentModerationService;

import java.security.Principal;
import java.util.List;

@Service
public class AiAssistantService {
    private final RagKnowledgeService knowledgeService;
    private final AiRemoteClient remoteClient;
    private final AiCallLogService callLogService;
    private final AiModelRegistryService modelRegistryService;
    private final ContentModerationService moderationService;

    public AiAssistantService(
            RagKnowledgeService knowledgeService,
            AiRemoteClient remoteClient,
            AiCallLogService callLogService,
            AiModelRegistryService modelRegistryService,
            ContentModerationService moderationService
    ) {
        this.knowledgeService = knowledgeService;
        this.remoteClient = remoteClient;
        this.callLogService = callLogService;
        this.modelRegistryService = modelRegistryService;
        this.moderationService = moderationService;
    }

    public AiAssistantResponse ask(String question, Principal principal) {
        long start = System.nanoTime();
        moderationService.checkConfigured("AI_INPUT", question, operator(principal));
        Refusal refusal = refusal(question);
        if (refusal != null) {
            moderateOutput(refusal.answer(), principal);
            callLogService.record(principal, "RAG_REFUSAL", question, "policy", elapsedMillis(start), true, refusal.reason());
            return response(refusal.answer(), List.of(), "policy", "REFUSAL", refusal.reason(), elapsedMillis(start));
        }
        List<AiSourceDocument> sources = knowledgeService.retrieve(question, principal);
        if (sources.isEmpty() || sources.stream().mapToDouble(AiSourceDocument::score).max().orElse(0) < 2.0) {
            String answer = "当前系统未检索到足够的教务依据，建议联系教务管理员或查看最新通知公告。";
            moderateOutput(answer, principal);
            callLogService.record(principal, "RAG_NO_EVIDENCE", question, "retrieval", elapsedMillis(start), true, "no matched source");
            return response(answer, sources, "retrieval", "NO_ANSWER", "知识库没有命中足够依据", elapsedMillis(start));
        }
        return remoteClient.ask(question, sources)
                .map(response -> {
                    String modelName = modelRegistryService.defaultModelName("RAG", response.modelName());
                    moderateOutput(response.answer(), principal);
                    callLogService.record(principal, "RAG", question, modelName, elapsedMillis(start), true, null);
                    return response(response.answer(), sources, response.serviceMode(), "ANSWER", null, elapsedMillis(start));
                })
                .orElseGet(() -> {
                    String answer = buildFallbackAnswer(question, sources);
                    moderateOutput(answer, principal);
                    callLogService.record(principal, "RAG_FALLBACK", question, "local-fallback", elapsedMillis(start), true, "ai-service unavailable");
                    return response(answer, sources, "local-fallback", "ANSWER", null, elapsedMillis(start));
                });
    }

    private void moderateOutput(String answer, Principal principal) {
        moderationService.checkConfigured("AI_OUTPUT", answer, operator(principal));
    }

    private String operator(Principal principal) {
        return principal == null ? "anonymous" : principal.getName();
    }

    private AiAssistantResponse response(
            String answer,
            List<AiSourceDocument> sources,
            String serviceMode,
            String answerType,
            String refusalReason,
            long latencyMs
    ) {
        double maxScore = sources.stream().mapToDouble(AiSourceDocument::score).max().orElse(0);
        String confidence = confidenceLevel(sources.size(), maxScore, serviceMode, answerType);
        return new AiAssistantResponse(
                answer,
                sources,
                serviceMode,
                answerType,
                refusalReason,
                confidence,
                Math.min(1.0, Math.max(0.0, maxScore / 5.0)),
                serviceMode,
                !serviceMode.contains("fallback") && !serviceMode.equals("retrieval") && !serviceMode.equals("policy"),
                serviceMode.contains("fallback") || serviceMode.equals("retrieval") || serviceMode.equals("policy"),
                latencyMs,
                TraceIdHolder.get(),
                null
        );
    }

    private String confidenceLevel(int evidenceCount, double maxScore, String serviceMode, String answerType) {
        if (!"ANSWER".equals(answerType) || serviceMode.contains("fallback") || evidenceCount == 0) {
            return "LOW";
        }
        if (evidenceCount >= 2 && maxScore >= 4.0) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private String buildFallbackAnswer(String question, List<AiSourceDocument> sources) {
        if (sources.isEmpty()) {
            return "暂时没有检索到可引用的教务资料。你可以换一个更具体的问题，例如“重修申请需要什么条件”或“为什么不能选课”。";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("根据当前教务资料，").append(resolveLead(question)).append("\n\n");
        for (int i = 0; i < Math.min(3, sources.size()); i++) {
            AiSourceDocument source = sources.get(i);
            builder.append(i + 1)
                    .append(". ")
                    .append(source.title())
                    .append("：")
                    .append(source.content())
                    .append("\n");
        }
        builder.append("\n以上是规则和业务数据检索结果。接入 ai-service/Ollama 后，这里会生成更自然的综合回答。");
        return builder.toString();
    }

    private String resolveLead(String question) {
        if (question == null) {
            return "可以参考以下内容：";
        }
        if (question.contains("毕业") || question.contains("学分")) {
            return "毕业和学分问题需要结合培养方案、已通过课程、挂科记录和毕业审核结果判断：";
        }
        if (question.contains("选课") || question.contains("退课")) {
            return "选课问题主要受选课时间、教学班容量、重复选课和已选记录影响：";
        }
        if (question.contains("申请") || question.contains("审核")) {
            return "申请类问题需要看申请类型、材料完整性、历史记录和管理员审核意见：";
        }
        return "可以参考以下内容：";
    }

    private Refusal refusal(String question) {
        if (question == null || question.isBlank()) {
            return new Refusal("请输入具体的教务问题。", "问题为空");
        }
        String q = question.toLowerCase(java.util.Locale.ROOT);
        if (q.contains("密码") || q.contains("token") || q.contains("密钥") || q.contains("身份证")
                || q.contains("其他学生") || q.contains("别人的") || q.contains("全校学生成绩")
                || q.contains("所有学生成绩") || q.contains("查询某个学生")) {
            return new Refusal("当前问题涉及非本人学业信息或敏感信息，系统不提供查询。", "涉及个人敏感或权限外数据");
        }
        String[] domainKeywords = {
                "教务", "课程", "选课", "退课", "抢课", "成绩", "考试", "学籍", "异动", "申请",
                "审核", "报名", "重修", "学分", "毕业", "培养方案", "教学计划", "评价", "通知",
                "公告", "课表", "教师", "学生", "专业", "班级"
        };
        for (String keyword : domainKeywords) {
            if (question.contains(keyword)) {
                return null;
            }
        }
        return new Refusal("当前助手主要支持教务规则、课程、选课、成绩、考试、申请审核等问题。", "非教务范围问题");
    }

    private long elapsedMillis(long startNanos) {
        return java.time.Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private record Refusal(String answer, String reason) {
    }
}
