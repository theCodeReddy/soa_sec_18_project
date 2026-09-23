package com.marketgrid.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootApplication @EnableDiscoveryClient
public class ProductApplication {
  public static void main(String[] args) { SpringApplication.run(ProductApplication.class, args); }
  record Product(String id, String vendorId, String tenantId, String name, String category, String description, BigDecimal price, int stock, boolean active, String imageUrl) {}
  record CreateProduct(@NotBlank String vendorId, @NotBlank String tenantId, @NotBlank String name, String category, String description, BigDecimal price, @Min(0) int stock) {}
  record Reserve(String productId, int quantity) {}
  @RestController @RequestMapping("/api/products") static class ProductController {
    private final JdbcTemplate jdbc;
    private final ProductImageService imageService;

    ProductController(JdbcTemplate jdbc, ProductImageService imageService) { this.jdbc = jdbc; this.imageService = imageService; }

    @PostMapping Product create(@Valid @RequestBody CreateProduct request) {
      var product = new Product(UUID.randomUUID().toString(), request.vendorId(), request.tenantId(), request.name(), request.category(), request.description(), request.price(), request.stock(), true, null);
      var generated = withGeneratedImage(product);
      jdbc.update("INSERT INTO products (id, vendor_id, tenant_id, name, category, description, price, stock, active, image_url) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", generated.id(), generated.vendorId(), generated.tenantId(), generated.name(), generated.category(), generated.description(), generated.price(), generated.stock(), generated.active(), generated.imageUrl());
      return generated;
    }
    @GetMapping(params = "id") Product get(@RequestParam String id) { return find(id); }
    @GetMapping(params = "vendorId") ListResponse list(@RequestParam String vendorId) { return new ListResponse(jdbc.query("SELECT id, vendor_id, tenant_id, name, category, description, price, stock, active, image_url FROM products WHERE vendor_id = ? AND active = TRUE", this::mapProduct, vendorId)); }
    @GetMapping(value = "/{id}/image", produces = MediaType.IMAGE_PNG_VALUE) ResponseEntity<byte[]> image(@PathVariable String id) {
      var product = find(id);
      if (product == null || product.imageUrl() == null) return ResponseEntity.notFound().build();
      try { return ResponseEntity.ok(Files.readAllBytes(imageService.pathFor(product.imageUrl()))); }
      catch (IOException error) { return ResponseEntity.notFound().build(); }
    }
    @PostMapping("/reserve") @Transactional Product reserve(@Valid @RequestBody Reserve request) { var old = find(request.productId()); var changed = jdbc.update("UPDATE products SET stock = stock - ? WHERE id = ? AND active = TRUE AND stock >= ?", request.quantity(), request.productId(), request.quantity()); if (changed == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock"); return new Product(old.id(), old.vendorId(), old.tenantId(), old.name(), old.category(), old.description(), old.price(), old.stock() - request.quantity(), old.active(), old.imageUrl()); }
    @PostMapping("/{id}/regenerate-image") Product regenerateImage(@PathVariable String id) {
      var product = find(id);
      var updated = withGeneratedImage(product);
      jdbc.update("UPDATE products SET image_url = ? WHERE id = ?", updated.imageUrl(), id);
      return updated;
    }

    private Product withGeneratedImage(Product product) {
      try { return product.imageUrl() == null ? imageService.generate(product) : product; }
      catch (RuntimeException error) { return product; }
    }
    private Product find(String id) { var products = jdbc.query("SELECT id, vendor_id, tenant_id, name, category, description, price, stock, active, image_url FROM products WHERE id = ?", this::mapProduct, id); if (products.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"); return products.get(0); }
    private Product mapProduct(java.sql.ResultSet result, int row) throws java.sql.SQLException { return new Product(result.getString("id"), result.getString("vendor_id"), result.getString("tenant_id"), result.getString("name"), result.getString("category"), result.getString("description"), result.getBigDecimal("price"), result.getInt("stock"), result.getBoolean("active"), result.getString("image_url")); }
  }
  record ListResponse(java.util.List<Product> products) {}

  @Service
  static class ProductImageService {
    private final RestClient client = RestClient.builder().baseUrl("https://api.openai.com/v1").build();
    private final String apiKey;
    private final Path imageDirectory;

    ProductImageService(@Value("${openai.api-key:}") String apiKey, @Value("${app.product-image-dir:uploads/products}") String imageDirectory) {
      this.apiKey = apiKey;
      this.imageDirectory = Path.of(imageDirectory);
    }

    Product generate(Product product) {
      if (apiKey.isBlank()) return product;
      var prompt = "Professional e-commerce product photo of \"%s\", category: %s. %s Clean studio lighting, white background, centered, realistic, no text or watermark.".formatted(product.name(), valueOrDefault(product.category(), "general merchandise"), valueOrDefault(product.description(), ""));
      JsonNode response = client.post().uri("/images/generations").contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + apiKey).body(Map.of("model", "gpt-image-1", "prompt", prompt, "size", "1024x1024")).retrieve().body(JsonNode.class);
      var encodedImage = response == null || response.path("data").isEmpty() ? null : response.path("data").get(0).path("b64_json").asText(null);
      if (encodedImage == null || encodedImage.isBlank()) throw new IllegalStateException("Image provider returned no image");
      try {
        Files.createDirectories(imageDirectory);
        var fileName = product.id() + "-" + System.currentTimeMillis() + ".png";
        Files.write(imageDirectory.resolve(fileName), Base64.getDecoder().decode(encodedImage));
        return new Product(product.id(), product.vendorId(), product.tenantId(), product.name(), product.category(), product.description(), product.price(), product.stock(), product.active(), "/product-images/" + fileName);
      } catch (IOException error) { throw new IllegalStateException("Unable to save generated product image", error); }
    }

    Path pathFor(String imageUrl) { return imageDirectory.resolve(Path.of(imageUrl).getFileName().toString()); }

    private String valueOrDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
  }
}
