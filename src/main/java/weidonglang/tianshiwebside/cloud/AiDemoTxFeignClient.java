package weidonglang.tianshiwebside.cloud;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "${app.ai-service.name:academic-ai-service}", contextId = "aiDemoTxFeignClient")
public interface AiDemoTxFeignClient {
    @PostMapping("/internal/demo-tx/insert")
    Map<String, Object> insert(@RequestParam("txNo") String txNo, @RequestParam("remark") String remark);

    @DeleteMapping("/internal/demo-tx/{txNo}")
    Map<String, Object> delete(@PathVariable("txNo") String txNo);

    @GetMapping("/internal/demo-tx/{txNo}/exists")
    Map<String, Object> exists(@PathVariable("txNo") String txNo);
}
