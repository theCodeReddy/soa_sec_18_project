package com.marketgrid.vendor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication @EnableDiscoveryClient
public class VendorApplication {
  public static void main(String[] args) { SpringApplication.run(VendorApplication.class, args); }
  record Vendor(String id, String tenantId, String name, String email, boolean active) {}
  record CreateVendor(@NotBlank String tenantId, @NotBlank String name, @NotBlank String email) {}
  @RestController @RequestMapping("/api/vendors") static class VendorController {
    private final JdbcTemplate jdbc;
    VendorController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @PostMapping Vendor create(@Valid @RequestBody CreateVendor request) { var vendor = new Vendor(UUID.randomUUID().toString(), request.tenantId(), request.name(), request.email(), true); jdbc.update("INSERT INTO vendors (id, tenant_id, name, email, active) VALUES (?, ?, ?, ?, ?)", vendor.id(), vendor.tenantId(), vendor.name(), vendor.email(), vendor.active()); return vendor; }
    @GetMapping(params = "id") Vendor get(@RequestParam String id) { return find(id); }
    @GetMapping(params = "tenantId") ListResponse list(@RequestParam String tenantId) { return new ListResponse(jdbc.query("SELECT id, tenant_id, name, email, active FROM vendors WHERE tenant_id = ?", this::mapVendor, tenantId)); }
    @PatchMapping("/{id}/status") Vendor status(@PathVariable String id, @RequestParam boolean active) { var vendor = find(id); jdbc.update("UPDATE vendors SET active = ? WHERE id = ?", active, id); return new Vendor(vendor.id(), vendor.tenantId(), vendor.name(), vendor.email(), active); }
    private Vendor find(String id) { var vendors = jdbc.query("SELECT id, tenant_id, name, email, active FROM vendors WHERE id = ?", this::mapVendor, id); if (vendors.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"); return vendors.get(0); }
    private Vendor mapVendor(java.sql.ResultSet result, int row) throws java.sql.SQLException { return new Vendor(result.getString("id"), result.getString("tenant_id"), result.getString("name"), result.getString("email"), result.getBoolean("active")); }
  }
  record ListResponse(java.util.List<Vendor> vendors) {}
}
