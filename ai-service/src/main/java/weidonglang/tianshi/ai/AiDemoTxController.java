package weidonglang.tianshi.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AiDemoTxController {
    private final JdbcTemplate jdbcTemplate;

    public AiDemoTxController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/internal/demo-tx/insert")
    @Transactional
    public Map<String, Object> insert(@RequestParam String txNo, @RequestParam(defaultValue = "seata-ai") String remark) {
        ensureTable();
        jdbcTemplate.update("delete from cloud_tx_demo_ai where tx_no = ?", txNo);
        jdbcTemplate.update(
                "insert into cloud_tx_demo_ai (tx_no, remark, created_at) values (?, ?, current_timestamp)",
                txNo,
                remark
        );
        return Map.of("txNo", txNo, "aiExists", true);
    }

    @DeleteMapping("/internal/demo-tx/{txNo}")
    @Transactional
    public Map<String, Object> delete(@PathVariable String txNo) {
        ensureTable();
        jdbcTemplate.update("delete from cloud_tx_demo_ai where tx_no = ?", txNo);
        return Map.of("txNo", txNo, "aiExists", false);
    }

    @GetMapping("/internal/demo-tx/{txNo}/exists")
    public Map<String, Object> exists(@PathVariable String txNo) {
        ensureTable();
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from cloud_tx_demo_ai where tx_no = ?",
                Integer.class,
                txNo
        );
        return Map.of("txNo", txNo, "aiExists", count != null && count > 0);
    }

    private void ensureTable() {
        jdbcTemplate.execute("""
                create table if not exists cloud_tx_demo_ai (
                    id bigint primary key auto_increment,
                    tx_no varchar(64) not null unique,
                    remark varchar(255) not null,
                    created_at timestamp not null default current_timestamp
                )
                """);
    }
}
