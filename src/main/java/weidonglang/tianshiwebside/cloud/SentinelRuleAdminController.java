package weidonglang.tianshiwebside.cloud;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import weidonglang.tianshiwebside.common.api.ApiResponse;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cloud-proof/sentinel")
public class SentinelRuleAdminController {

    @GetMapping("/login-rule")
    public ApiResponse<Map<String, Object>> currentLoginRule() {
        List<Map<String, Object>> rules = loginRules();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resource", SentinelLoginRuleConfig.LOGIN_RESOURCE);
        data.put("rules", rules);
        data.put("currentQps", rules.isEmpty() ? null : rules.get(0).get("qps"));
        return ApiResponse.success(data);
    }

    @PostMapping("/login-rule")
    public ApiResponse<Map<String, Object>> updateLoginRule(@RequestParam double qps) {
        if (qps <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qps must be greater than 0");
        }

        FlowRule rule = new FlowRule();
        rule.setResource(SentinelLoginRuleConfig.LOGIN_RESOURCE);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(qps);
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        FlowRuleManager.loadRules(List.of(rule));

        return ApiResponse.success(Map.of(
                "resource", SentinelLoginRuleConfig.LOGIN_RESOURCE,
                "qps", qps,
                "grade", "QPS",
                "message", "Sentinel login flow rule updated"
        ));
    }

    private List<Map<String, Object>> loginRules() {
        return FlowRuleManager.getRules().stream()
                .filter(rule -> SentinelLoginRuleConfig.LOGIN_RESOURCE.equals(rule.getResource()))
                .map(rule -> Map.<String, Object>of(
                        "resource", rule.getResource(),
                        "qps", rule.getCount(),
                        "grade", rule.getGrade() == RuleConstant.FLOW_GRADE_QPS ? "QPS" : rule.getGrade(),
                        "controlBehavior", rule.getControlBehavior()
                ))
                .toList();
    }
}
