package weidonglang.tianshiwebside;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

abstract class HttpRegressionTestSupport {
    protected final HttpClient httpClient = HttpClient.newHttpClient();
    protected final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @LocalServerPort
    protected int port;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected String login(String username) throws Exception {
        JsonNode body = json(post("/api/auth/login", null, """
                {"username":"%s","password":"123456"}
                """.formatted(username)), HttpStatus.OK);
        return body.at("/data/accessToken").asText();
    }

    protected JsonNode json(HttpResponse<String> response, HttpStatus expectedStatus) throws Exception {
        assertThat(response.statusCode()).isEqualTo(expectedStatus.value());
        return objectMapper.readTree(response.body());
    }

    protected HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        authorize(builder, token);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        authorize(builder, token);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> put(String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        authorize(builder, token);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> delete(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).DELETE();
        authorize(builder, token);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    protected void authorize(HttpRequest.Builder builder, String token) {
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
    }

    protected void seedStudent(String username, String displayName) {
        seedUser(username, displayName, List.of("STUDENT"), List.of());
        Long userId = jdbcTemplate.queryForObject("select id from sys_user where username = ?", Long.class, username);
        jdbcTemplate.update("""
                        insert into student (user_id, student_no, college, major, class_name, grade, status, phone, email, address)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                userId, username, "信息工程学院", "软件工程", "软件工程 23-1", "2023", "在籍",
                "13800000000", username + "@example.com", "天津");
    }

    protected void seedUser(String username, String displayName, List<String> roleCodes, List<String> permissionCodes) {
        jdbcTemplate.update("""
                        insert into sys_user (username, password_hash, display_name, status)
                        values (?, ?, ?, ?)
                        """,
                username, passwordEncoder.encode("123456"), displayName, "ACTIVE");
        addRoles(username, roleCodes);
        if (!permissionCodes.isEmpty() && !roleCodes.isEmpty()) {
            addPermissions(roleCodes.get(0), permissionCodes);
        }
    }

    protected void addRoles(String username, List<String> roleCodes) {
        Long userId = jdbcTemplate.queryForObject("select id from sys_user where username = ?", Long.class, username);
        for (String roleCode : roleCodes) {
            ensureRole(roleCode);
            Long roleId = jdbcTemplate.queryForObject("select id from sys_role where code = ?", Long.class, roleCode);
            Integer count = jdbcTemplate.queryForObject("""
                            select count(*)
                            from sys_user_role
                            where user_id = ? and role_id = ?
                            """,
                    Integer.class, userId, roleId);
            if (count == null || count == 0) {
                jdbcTemplate.update("insert into sys_user_role (user_id, role_id) values (?, ?)", userId, roleId);
            }
        }
    }

    protected void ensureRole(String roleCode) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from sys_role where code = ?", Integer.class, roleCode);
        if (count == null || count == 0) {
            jdbcTemplate.update("insert into sys_role (code, name) values (?, ?)", roleCode, roleCode);
        }
    }

    protected void addPermissions(String roleCode, List<String> permissionCodes) {
        ensureRole(roleCode);
        Long roleId = jdbcTemplate.queryForObject("select id from sys_role where code = ?", Long.class, roleCode);
        for (String permissionCode : permissionCodes) {
            Integer permissionCount = jdbcTemplate.queryForObject("select count(*) from sys_permission where code = ?", Integer.class, permissionCode);
            if (permissionCount == null || permissionCount == 0) {
                jdbcTemplate.update("insert into sys_permission (code, name, description) values (?, ?, ?)",
                        permissionCode, permissionCode, permissionCode);
            }
            Long permissionId = jdbcTemplate.queryForObject("select id from sys_permission where code = ?", Long.class, permissionCode);
            Integer linkCount = jdbcTemplate.queryForObject("""
                            select count(*)
                            from sys_role_permission
                            where role_id = ? and permission_id = ?
                            """,
                    Integer.class, roleId, permissionId);
            if (linkCount == null || linkCount == 0) {
                jdbcTemplate.update("insert into sys_role_permission (role_id, permission_id) values (?, ?)", roleId, permissionId);
            }
        }
    }

    protected String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
