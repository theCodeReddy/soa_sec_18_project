package com.marketgrid.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootApplication @EnableDiscoveryClient
public class ProductApplication {
  public static void main(String[] args) { SpringApplication.run(ProductApplication.class, args); }
  @Configuration
  static class HttpClientConfiguration {
    @Bean @LoadBalanced RestClient.Builder loadBalancedRestClientBuilder() { return RestClient.builder(); }
  }
  record Product(String id, String vendorId, String tenantId, String name, String category, String description, BigDecimal price, int stock, boolean active, String imageUrl) {}
  record CreateProduct(@NotBlank String vendorId, @NotBlank String tenantId, @NotBlank String name, String category, String description, BigDecimal price, @Min(0) int stock, String imageUrl) {}
  record UpdateImage(@NotBlank String imageUrl) {}
  record Reserve(String productId, int quantity) {}
  @RestController @RequestMapping("/api/products") static class ProductController {
    private final JdbcTemplate jdbc;
    private final ProductImageService imageService;

    ProductController(JdbcTemplate jdbc, ProductImageService imageService) { this.jdbc = jdbc; this.imageService = imageService; }

    @PostMapping Product create(@Valid @RequestBody CreateProduct request) {
      var exactImageUrl = request.imageUrl() == null || request.imageUrl().isBlank() ? null : request.imageUrl().trim();
      var product = new Product(UUID.randomUUID().toString(), request.vendorId(), request.tenantId(), request.name(), request.category(), request.description(), request.price(), request.stock(), true, exactImageUrl);
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
      var source = new Product(product.id(), product.vendorId(), product.tenantId(), product.name(), product.category(), product.description(), product.price(), product.stock(), product.active(), null);
      Product updated;
      try { updated = imageService.generate(source); }
      catch (RuntimeException error) {
        imageService.logFailure(source, error);
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image generation is currently unavailable");
      }
      if (updated.imageUrl() == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image generation returned no image");
      jdbc.update("UPDATE products SET image_url = ? WHERE id = ?", updated.imageUrl(), id);
      return updated;
    }

    @PatchMapping("/{id}/image") Product updateImage(@PathVariable String id, @Valid @RequestBody UpdateImage request) {
      var product = find(id);
      jdbc.update("UPDATE products SET image_url = ? WHERE id = ?", request.imageUrl(), id);
      return new Product(product.id(), product.vendorId(), product.tenantId(), product.name(), product.category(), product.description(), product.price(), product.stock(), product.active(), request.imageUrl());
    }

    private Product withGeneratedImage(Product product) {
      if (product.imageUrl() != null) return product;
      try { return imageService.generate(product); }
      catch (RuntimeException error) {
        imageService.logFailure(product, error);
        return product;
      }
    }
    private Product find(String id) { var products = jdbc.query("SELECT id, vendor_id, tenant_id, name, category, description, price, stock, active, image_url FROM products WHERE id = ?", this::mapProduct, id); if (products.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"); return products.get(0); }
    private Product mapProduct(java.sql.ResultSet result, int row) throws java.sql.SQLException { return new Product(result.getString("id"), result.getString("vendor_id"), result.getString("tenant_id"), result.getString("name"), result.getString("category"), result.getString("description"), result.getBigDecimal("price"), result.getInt("stock"), result.getBoolean("active"), result.getString("image_url")); }
  }
  record ListResponse(java.util.List<Product> products) {}

  @Service
  static class ProductImageService {
    private static final Logger log = LoggerFactory.getLogger(ProductImageService.class);
    private final RestClient client;
    private final Path imageDirectory;

    ProductImageService(RestClient.Builder clientBuilder, @Value("${app.image-service-url:http://image-service}") String imageServiceUrl, @Value("${app.product-image-dir:uploads/products}") String imageDirectory) {
      var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
      var requestFactory = new JdkClientHttpRequestFactory(httpClient);
      requestFactory.setReadTimeout(Duration.ofSeconds(90));
      this.client = clientBuilder.baseUrl(imageServiceUrl).requestFactory(requestFactory).build();
      this.imageDirectory = Path.of(imageDirectory);
    }

    Product generate(Product product) {
      log.info("Generating image for product: {}", product.name());
      var request = new LinkedHashMap<String, String>();
      request.put("name", valueOrDefault(product.name(), "Unnamed product"));
      request.put("category", valueOrDefault(product.category(), "general merchandise"));
      request.put("description", valueOrDefault(product.description(), ""));
      log.info("Calling Image Service for product: {}", product.name());
      byte[] imageBytes;
      try {
        imageBytes = client.post().uri("/api/images/generate").contentType(MediaType.APPLICATION_JSON).body(request).retrieve()
            .onStatus(status -> status.value() >= 400, (requestMessage, response) -> {
              log.error("Image Service returned status {} for product {}", response.getStatusCode().value(), product.name());
              throw new ImageGenerationException("Image Service returned HTTP " + response.getStatusCode().value());
            }).body(byte[].class);
      } catch (ResourceAccessException error) {
        throw new ImageGenerationException("Image Service unavailable or timed out", error);
      } catch (RestClientResponseException error) {
        throw new ImageGenerationException("Image Service returned HTTP " + error.getStatusCode().value(), error);
      }
      if (imageBytes == null || imageBytes.length == 0) throw new ImageGenerationException("Image Service returned an empty image");
      try {
        Files.createDirectories(imageDirectory);
        var fileName = product.id() + "-" + System.currentTimeMillis() + ".png";
        Files.write(imageDirectory.resolve(fileName), imageBytes);
        log.info("Image generation successful for product: {}", product.name());
        return new Product(product.id(), product.vendorId(), product.tenantId(), product.name(), product.category(), product.description(), product.price(), product.stock(), product.active(), "/product-images/" + fileName);
      } catch (IOException error) { throw new ImageGenerationException("Unable to save generated product image", error); }
    }

    void logFailure(Product product, RuntimeException error) { log.error("Image generation failed for product: {} - {}", product.name(), error.getMessage()); }

    Path pathFor(String imageUrl) { return imageDirectory.resolve(Path.of(imageUrl).getFileName().toString()); }

    private String valueOrDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
  }

  static class ImageGenerationException extends RuntimeException {
    ImageGenerationException(String message) { super(message); }
    ImageGenerationException(String message, Throwable cause) { super(message, cause); }
  }
}
