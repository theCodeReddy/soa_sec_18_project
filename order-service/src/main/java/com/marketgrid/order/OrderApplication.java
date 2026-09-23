package com.marketgrid.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootApplication @EnableDiscoveryClient @EnableFeignClients
public class OrderApplication {
  public static void main(String[] args) { SpringApplication.run(OrderApplication.class, args); }
  record OrderItem(@NotBlank String productId, @NotBlank String vendorId, @Min(1) int quantity, BigDecimal unitPrice) {}
  record PlaceOrder(@NotBlank String customerId, @NotBlank String tenantId, List<@Valid OrderItem> items) {}
  record Order(String id, String customerId, String tenantId, List<OrderItem> items, String status, Instant createdAt) {}
  record Reserve(String productId, int quantity) {}
  @FeignClient(name="product-service") interface ProductClient { @PostMapping("/api/products/reserve") Map<String,Object> reserve(@RequestBody Reserve request); }
  @RestController @RequestMapping("/api/orders") static class OrderController {
    private final JdbcTemplate jdbc; private final ProductClient products;
    OrderController(JdbcTemplate jdbc, ProductClient products) { this.jdbc = jdbc; this.products = products; }
    @PostMapping @Transactional Order place(@Valid @RequestBody PlaceOrder request) { if (request.items() == null || request.items().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order needs at least one item"); request.items().forEach(item -> { try { products.reserve(new Reserve(item.productId(), item.quantity())); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Could not reserve product " + item.productId()); } }); var order = new Order(UUID.randomUUID().toString(), request.customerId(), request.tenantId(), request.items(), "CONFIRMED", Instant.now()); jdbc.update("INSERT INTO orders (id, customer_id, tenant_id, status, created_at) VALUES (?, ?, ?, ?, ?)", order.id(), order.customerId(), order.tenantId(), order.status(), order.createdAt()); request.items().forEach(item -> jdbc.update("INSERT INTO order_items (order_id, product_id, vendor_id, quantity, unit_price) VALUES (?, ?, ?, ?, ?)", order.id(), item.productId(), item.vendorId(), item.quantity(), item.unitPrice())); return order; }
    @GetMapping("/{id}") Order track(@PathVariable String id) { return find(id); }
    @GetMapping List<Order> byCustomer(@RequestParam String customerId) { return jdbc.query("SELECT id, customer_id, tenant_id, status, created_at FROM orders WHERE customer_id = ? ORDER BY created_at DESC", this::mapOrder, customerId); }
    private Order find(String id) { var orders = jdbc.query("SELECT id, customer_id, tenant_id, status, created_at FROM orders WHERE id = ?", this::mapOrder, id); if (orders.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"); return orders.get(0); }
    private Order mapOrder(java.sql.ResultSet result, int row) throws java.sql.SQLException { var id = result.getString("id"); var items = jdbc.query("SELECT product_id, vendor_id, quantity, unit_price FROM order_items WHERE order_id = ?", (item, itemRow) -> new OrderItem(item.getString("product_id"), item.getString("vendor_id"), item.getInt("quantity"), item.getBigDecimal("unit_price")), id); return new Order(id, result.getString("customer_id"), result.getString("tenant_id"), items, result.getString("status"), result.getTimestamp("created_at").toInstant()); }
  }
}
