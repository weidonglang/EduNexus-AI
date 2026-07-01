package weidonglang.tianshiwebside.cloud;

import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CloudProofTransactionService {
    private final JdbcTemplate jdbcTemplate;
    private final AiDemoTxFeignClient aiDemoTxFeignClient;

    public CloudProofTransactionService(JdbcTemplate jdbcTemplate, AiDemoTxFeignClient aiDemoTxFeignClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiDemoTxFeignClient = aiDemoTxFeignClient;
    }

    @GlobalTransactional(name = "edunexus-cloud-proof-commit", rollbackFor = Exception.class)
    @Transactional
    public void commit(String txNo) {
        insertMain(txNo, "seata-main-commit");
        aiDemoTxFeignClient.insert(txNo, "seata-ai-commit");
    }

    @GlobalTransactional(name = "edunexus-cloud-proof-rollback", rollbackFor = Exception.class)
    @Transactional
    public void rollback(String txNo) {
        insertMain(txNo, "seata-main-rollback");
        aiDemoTxFeignClient.insert(txNo, "seata-ai-rollback");
        throw new IllegalStateException("intentional rollback for Seata proof");
    }

    private void insertMain(String txNo, String remark) {
        jdbcTemplate.update(
                "insert into cloud_tx_demo_main (tx_no, remark, created_at) values (?, ?, current_timestamp)",
                txNo,
                remark
        );
    }
}
