package weidonglang.tianshiwebside.ai;

import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;

@Service
public class AiAssistantService {
    private final RagKnowledgeService knowledgeService;
    private final AiRemoteClient remoteClient;
    private final AiCallLogService callLogService;

    public AiAssistantService(RagKnowledgeService knowledgeService, AiRemoteClient remoteClient, AiCallLogService callLogService) {
        this.knowledgeService = knowledgeService;
        this.remoteClient = remoteClient;
        this.callLogService = callLogService;
    }

    public AiAssistantResponse ask(String question, Principal principal) {
        long start = System.nanoTime();
        Refusal refusal = refusal(question);
        if (refusal != null) {
            callLogService.record(principal, "RAG_REFUSAL", question, "policy", elapsedMillis(start), true, refusal.reason());
            return new AiAssistantResponse(refusal.answer(), List.of(), "policy", "REFUSAL", refusal.reason());
        }
        List<AiSourceDocument> sources = knowledgeService.retrieve(question, principal);
        if (sources.isEmpty() || sources.stream().mapToDouble(AiSourceDocument::score).max().orElse(0) < 2.0) {
            String answer = "当前系统未检索到足够的教务依据，建议联系教务管理员或查看最新通知公告。";
            callLogService.record(principal, "RAG_NO_EVIDENCE", question, "retrieval", elapsedMillis(start), true, "no matched source");
            return new AiAssistantResponse(answer, sources, "retrieval", "NO_ANSWER", "知识库没有命中足够依据");
        }
        return remoteClient.ask(question, sources)
                .map(response -> {
                    callLogService.record(principal, "RAG", question, response.serviceMode(), elapsedMillis(start), true, null);
                    return new AiAssistantResponse(response.answer(), sources, response.serviceMode(), "ANSWER", null);
                })
                .orElseGet(() -> {
                    callLogService.record(principal, "RAG_FALLBACK", question, "local-fallback", elapsedMillis(start), true, "ai-service unavailable");
                    return new AiAssistantResponse(buildFallbackAnswer(question, sources), sources, "local-fallback", "ANSWER", null);
                });
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
