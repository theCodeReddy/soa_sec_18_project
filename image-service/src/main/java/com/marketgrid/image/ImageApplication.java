package com.marketgrid.image;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@SpringBootApplication @EnableDiscoveryClient
public class ImageApplication {
  public static void main(String[] args) { SpringApplication.run(ImageApplication.class, args); }

  record ImageRequest(@NotBlank String name, String category, String description) {}

  @RestController @RequestMapping("/api/images")
  static class ImageController {
    private static final Logger log = LoggerFactory.getLogger(ImageController.class);
    private final RestClient client;
    private final String apiKey;
    private final String model;

    ImageController(@Value("${image.api-base-url:https://api.openai.com/v1}") String apiBaseUrl,
                    @Value("${image.api-key:}") String apiKey,
                    @Value("${image.api-model:gpt-image-1}") String model) {
      var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
      var requestFactory = new JdkClientHttpRequestFactory(httpClient);
      requestFactory.setReadTimeout(Duration.ofSeconds(120));
      this.client = RestClient.builder().baseUrl(apiBaseUrl).requestFactory(requestFactory).build();
      this.apiKey = apiKey;
      this.model = model;
    }

    @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.IMAGE_PNG_VALUE)
    ResponseEntity<byte[]> generate(@Valid @RequestBody ImageRequest request) {
      if (apiKey.isBlank()) {
        log.error("Image generation rejected because IMAGE_API_KEY is not configured");
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image API is not configured");
      }
      var prompt = buildPrompt(request);
      log.info("Generating image for product: {} using model {}", request.name(), model);
      log.debug("Generated image prompt for product {}: {}", request.name(), prompt);
      var body = new LinkedHashMap<String, String>();
      body.put("model", model);
      body.put("prompt", prompt);
      body.put("size", "1024x1024");
      try {
        JsonNode response = client.post().uri("/images/generations").contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + apiKey).body(body).retrieve()
            .onStatus(status -> status.value() >= 400, (requestMessage, externalResponse) -> {
              var status = externalResponse.getStatusCode().value();
              log.error("External image API returned status: {}", status);
              throw new ExternalImageApiException(status);
            }).body(JsonNode.class);
        return ResponseEntity.ok(readImage(response, request.name()));
      } catch (ExternalImageApiException error) {
        throw mapExternalError(error.status());
      } catch (ResourceAccessException error) {
        log.error("External image API timed out or was unavailable", error);
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Image API is unavailable", error);
      } catch (RestClientResponseException error) {
        log.error("External image API returned status: {}", error.getStatusCode().value());
        throw mapExternalError(error.getStatusCode().value());
      }
    }

    private byte[] readImage(JsonNode response, String productName) {
      var data = response == null ? null : response.path("data");
      var first = data == null || data.isEmpty() ? null : data.get(0);
      var encoded = first == null ? null : first.path("b64_json").asText(null);
      if (encoded != null && !encoded.isBlank()) {
        try { return Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException error) {
          log.error("External image API returned invalid base64 image data for product {}", productName);
          throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Image API returned invalid image data");
        }
      }
      var imageUrl = first == null ? null : first.path("url").asText(null);
      if (imageUrl != null && !imageUrl.isBlank()) {
        try {
          var image = RestClient.create().get().uri(imageUrl).retrieve().body(byte[].class);
          if (image != null && image.length > 0) return image;
        } catch (RestClientException error) {
          log.error("Could not download image URL returned for product {}", productName, error);
        }
      }
      log.error("External image API returned an empty image response for product {}", productName);
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Image API returned no image");
    }

    private String buildPrompt(ImageRequest request) {
      return "Create a professional e-commerce product image of %s. Category: %s. Description: %s. Show only the product, centered, clean studio background, realistic commercial product photography, suitable for an online shopping website, no text or watermark."
          .formatted(valueOrDefault(request.name(), "unnamed product"), valueOrDefault(request.category(), "general merchandise"), valueOrDefault(request.description(), "No additional description provided"));
    }

    private ResponseStatusException mapExternalError(int status) {
      if (status == 401 || status == 403) return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Image API authentication failed");
      if (status == 429) return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image API rate limit exceeded");
      if (status >= 500) return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Image API failed");
      return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Image API request failed");
    }

    private String valueOrDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
  }

  static class ExternalImageApiException extends RuntimeException {
    private final int status;
    ExternalImageApiException(int status) { this.status = status; }
    int status() { return status; }
  }
}
