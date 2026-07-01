package weidonglang.tianshiwebside.cloud;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import weidonglang.tianshiwebside.ai.AiServiceFeignClient;
import weidonglang.tianshiwebside.common.api.ApiResponse;

import java.util.Map;

@RestController
@RequestMapping("/api/cloud-proof")
public class CloudProofController {
    private final CloudProofService cloudProofService;
    private final AiServiceFeignClient aiServiceFeignClient;

    public CloudProofController(CloudProofService cloudProofService, AiServiceFeignClient aiServiceFeignClient) {
        this.cloudProofService = cloudProofService;
        this.aiServiceFeignClient = aiServiceFeignClient;
    }

    @GetMapping("/feign/ai-status")
    public ApiResponse<Map<String, Object>> aiStatusViaFeign() {
        return ApiResponse.success(Map.of(
                "transport", "OpenFeign",
                "targetService", "academic-ai-service",
                "aiStatus", aiServiceFeignClient.status()
        ));
    }

    @RequestMapping("/seata/commit")
    public ApiResponse<CloudProofService.CloudTxProofResult> seataCommit() {
        return ApiResponse.success(cloudProofService.commit());
    }

    @RequestMapping("/seata/rollback")
    public ApiResponse<CloudProofService.CloudTxProofResult> seataRollback() {
        return ApiResponse.success(cloudProofService.rollback());
    }
}
