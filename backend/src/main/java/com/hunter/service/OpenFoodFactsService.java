package com.hunter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class OpenFoodFactsService {

    private static final Pattern GTIN = Pattern.compile("(?:[0-9]{8}|[0-9]{12,14})");
    private static final String PRODUCT_FIELDS =
            "code,product_name,product_name_en,brands,quantity,ingredients_text,ingredients_text_en,"
                    + "allergens,allergens_en,categories,nutriments";
    private static final Map<String, String> NUTRIENT_FIELDS = Map.of(
            "energy-kcal_100g", "Energy (kcal/100g)",
            "fat_100g", "Fat (g/100g)",
            "saturated-fat_100g", "Saturated fat (g/100g)",
            "carbohydrates_100g", "Carbohydrates (g/100g)",
            "sugars_100g", "Sugars (g/100g)",
            "fiber_100g", "Fiber (g/100g)",
            "proteins_100g", "Protein (g/100g)",
            "salt_100g", "Salt (g/100g)"
    );

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI baseUri;

    public OpenFoodFactsService(
            ObjectMapper objectMapper,
            @Value("${hunter.product-lookup.base-url:https://world.openfoodfacts.org}") String baseUrl
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.baseUri = URI.create(baseUrl);
    }

    public ProductDetails lookup(String barcode) {
        String normalizedBarcode = barcode == null ? "" : barcode.strip();
        if (!isValidGtin(normalizedBarcode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter a valid 8, 12, 13, or 14 digit product barcode.");
        }

        URI uri = baseUri.resolve("/api/v2/product/" + normalizedBarcode + ".json?fields=" + PRODUCT_FIELDS);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("User-Agent", "HUNTER-Label-Review/0.1 (local prototype)")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Open Food Facts could not complete the product lookup. Try again later.");
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.path("status").asInt(0) != 1 || !root.path("product").isObject()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No Open Food Facts product record was found for this barcode. Add the details manually.");
            }
            JsonNode product = root.path("product");
            Map<String, String> nutrients = new LinkedHashMap<>();
            JsonNode nutrientData = product.path("nutriments");
            NUTRIENT_FIELDS.forEach((key, label) -> {
                String value = text(nutrientData.path(key));
                if (!value.isBlank()) {
                    nutrients.put(label, value);
                }
            });
            return new ProductDetails(
                    normalizedBarcode,
                    firstText(product, "product_name_en", "product_name"),
                    text(product.path("brands")),
                    text(product.path("quantity")),
                    firstText(product, "ingredients_text_en", "ingredients_text"),
                    firstText(product, "allergens_en", "allergens"),
                    text(product.path("categories")),
                    nutrients,
                    "https://world.openfoodfacts.org/product/" + normalizedBarcode,
                    "Product information is community-contributed and may be incomplete or out of date. Verify every detail against the package."
            );
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Product lookup was interrupted. Try again.", error);
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not reach Open Food Facts. Check your internet connection or enter product details manually.", error);
        }
    }

    static boolean isValidGtin(String value) {
        if (value == null || !GTIN.matcher(value).matches()) {
            return false;
        }
        int sum = 0;
        int weight = 3;
        for (int index = value.length() - 2; index >= 0; index--) {
            sum += Character.digit(value.charAt(index), 10) * weight;
            weight = weight == 3 ? 1 : 3;
        }
        int expectedCheckDigit = (10 - sum % 10) % 10;
        return Character.digit(value.charAt(value.length() - 1), 10) == expectedCheckDigit;
    }

    private static String firstText(JsonNode object, String... fields) {
        for (String field : fields) {
            String value = text(object.path(field));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    static String text(JsonNode value) {
        return value.isTextual() || value.isNumber() ? value.asText().strip() : "";
    }

    public record ProductDetails(
            String barcode,
            String productName,
            String brands,
            String quantity,
            String ingredients,
            String allergens,
            String categories,
            Map<String, String> nutrients,
            String sourceUrl,
            String notice
    ) {
    }
}
