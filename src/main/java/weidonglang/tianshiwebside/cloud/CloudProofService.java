package weidonglang.tianshiwebside.cloud;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class CloudProofService {
    private final JdbcTemplate jdbcTemplate;
    private final AiDemoTxFeignClient aiDemoTxFeignClient;
    private final CloudProofTransactionService transactionService;
    private final boolean seataEnabled;

    public CloudProofService(
            JdbcTemplate jdbcTemplate,
            AiDemoTxFeignClient aiDemoTxFeignClient,
            CloudProofTransactionService transactionService,
            @Value("${seata.enabled:false}") boolean seataEnabled
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiDemoTxFeignClient = aiDemoTxFeignClient;
        this.transactionService = transactionService;
        this.seataEnabled = seataEnabled;
    }

    public CloudTxProofResult commit() {
        String txNo = newTxNo("commit");
        deleteMain(txNo);
        aiDemoTxFeignClient.delete(txNo);
        transactionService.commit(txNo);
        return buildResult("commit", txNo, false, null);
    }

    public CloudTxProofResult rollback() {
        String txNo = newTxNo("rollback");
        deleteMain(txNo);
        aiDemoTxFeignClient.delete(txNo);
        String error = null;
        try {
            transactionService.rollback(txNo);
        } catch (RuntimeException ex) {
            error = ex.getMessage();
            if (!seataEnabled) {
                deleteMain(txNo);
                aiDemoTxFeignClient.delete(txNo);
            }
        }
        return buildResult("rollback", txNo, true, error);
    }

    private void deleteMain(String txNo) {
        jdbcTemplate.update("delete from cloud_tx_demo_main where tx_no = ?", txNo);
    }

    private boolean mainExists(String txNo) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from cloud_tx_demo_main where tx_no = ?",
                Integer.class,
                txNo
        );
        return count != null && count > 0;
    }

    private boolean aiExists(String txNo) {
        Map<String, Object> response = aiDemoTxFeignClient.exists(txNo);
        Object value = response.get("aiExists");
        return value instanceof Boolean exists ? exists : Boolean.parseBoolean(String.valueOf(value));
    }

    private CloudTxProofResult buildResult(String action, String txNo, boolean rollbackExpected, String error) {
        boolean mainExists = mainExists(txNo);
        boolean aiExists = aiExists(txNo);
        return new CloudTxProofResult(
                action,
                txNo,
                seataEnabled,
                rollbackExpected,
                mainExists,
                aiExists,
                error
        );
    }

    private String newTxNo(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 12);
    }

    public record CloudTxProofResult(
            String action,
            String txNo,
            boolean seataEnabled,
            boolean rollbackExpected,
            boolean mainExists,
            boolean aiExists,
            String error
    ) {
    }
}
