package weidonglang.tianshiwebside.cloud;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SentinelLoginRuleConfig {
    public static final String LOGIN_RESOURCE = "authLogin";

    private final boolean enabled;
    private final double loginQps;

    public SentinelLoginRuleConfig(
            @Value("${app.cloud-proof.sentinel.enabled:true}") boolean enabled,
            @Value("${app.cloud-proof.sentinel.login-qps:3}") double loginQps
    ) {
        this.enabled = enabled;
        this.loginQps = loginQps;
    }

    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    @PostConstruct
    void loadLoginFlowRule() {
        if (!enabled) {
            return;
        }
        FlowRule rule = new FlowRule();
        rule.setResource(LOGIN_RESOURCE);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(loginQps);
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        FlowRuleManager.loadRules(List.of(rule));
    }
}
