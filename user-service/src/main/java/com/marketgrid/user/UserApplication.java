package com.marketgrid.user;

import com.marketgrid.common.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication
@EnableDiscoveryClient
public class UserApplication {
    public static void main(String[] args) { SpringApplication.run(UserApplication.class, args); }
    record Register(@NotBlank String name, @Email @NotBlank String email, @NotBlank String password, String tenantId) {}
    record Login(@Email @NotBlank String email, @NotBlank String password) {}
    record User(String id, String name, String email, String password, String tenantId, String role) {}
    record UserResponse(String id, String name, String email, String tenantId, String role) {}
    @RestController @RequestMapping("/api/auth")
    static class AuthController {
        private final JdbcTemplate jdbc;
        private final JwtService jwt = new JwtService();
        AuthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
        @PostMapping("/register") UserResponse register(@Valid @RequestBody Register request) { var user = new User(UUID.randomUUID().toString(), request.name(), request.email(), request.password(), request.tenantId() == null ? "default" : request.tenantId(), "CUSTOMER"); jdbc.update("INSERT INTO users (id, name, email, password, tenant_id, role) VALUES (?, ?, ?, ?, ?, ?)", user.id(), user.name(), user.email(), user.password(), user.tenantId(), user.role()); return new UserResponse(user.id(), user.name(), user.email(), user.tenantId(), user.role()); }
        @PostMapping("/login") Map<String, String> login(@Valid @RequestBody Login request) { var users = jdbc.query("SELECT id, name, email, password, tenant_id, role FROM users WHERE email = ?", (result, row) -> new User(result.getString("id"), result.getString("name"), result.getString("email"), result.getString("password"), result.getString("tenant_id"), result.getString("role")), request.email()); if (users.isEmpty() || !users.get(0).password().equals(request.password())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"); var user = users.get(0); return Map.of("token", jwt.issue(user.email(), user.role(), user.tenantId(), null), "customerId", user.id(), "tenantId", user.tenantId(), "name", user.name()); }
    }
}
