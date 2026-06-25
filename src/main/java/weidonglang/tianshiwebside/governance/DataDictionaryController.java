package weidonglang.tianshiwebside.governance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import weidonglang.tianshiwebside.common.api.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/api/admin/data-dictionary")
@PreAuthorize("hasRole('ADMIN')")
public class DataDictionaryController {
    private final JdbcTemplate jdbcTemplate;

    public DataDictionaryController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/tables")
    public ApiResponse<List<DictionaryTableRow>> tables(@RequestParam(required = false) String module) {
        String normalizedModule = module == null || module.isBlank() ? null : module.trim();
        List<DictionaryTableRow> rows = jdbcTemplate.query("""
                        select table_name, display_name, module, description, sensitive_level, export_allowed
                        from data_dictionary_table
                        where (? is null or module = ?)
                        order by module asc, table_name asc
                        """,
                (rs, rowNum) -> new DictionaryTableRow(
                        rs.getString("table_name"),
                        rs.getString("display_name"),
                        rs.getString("module"),
                        rs.getString("description"),
                        rs.getString("sensitive_level"),
                        rs.getBoolean("export_allowed")
                ),
                normalizedModule,
                normalizedModule
        );
        return ApiResponse.success(rows);
    }

    @GetMapping("/tables/{tableName}/fields")
    public ApiResponse<List<DictionaryFieldRow>> fields(@PathVariable String tableName) {
        return ApiResponse.success(jdbcTemplate.query("""
                        select table_name, field_name, display_name, description, is_sensitive, masking_rule, export_allowed
                        from data_dictionary_field
                        where table_name = ?
                        order by field_name asc
                        """,
                (rs, rowNum) -> new DictionaryFieldRow(
                        rs.getString("table_name"),
                        rs.getString("field_name"),
                        rs.getString("display_name"),
                        rs.getString("description"),
                        rs.getBoolean("is_sensitive"),
                        rs.getString("masking_rule"),
                        rs.getBoolean("export_allowed")
                ),
                tableName
        ));
    }

    public record DictionaryTableRow(
            String tableName,
            String displayName,
            String module,
            String description,
            String sensitiveLevel,
            boolean exportAllowed
    ) {
    }

    public record DictionaryFieldRow(
            String tableName,
            String fieldName,
            String displayName,
            String description,
            boolean sensitive,
            String maskingRule,
            boolean exportAllowed
    ) {
    }
}
