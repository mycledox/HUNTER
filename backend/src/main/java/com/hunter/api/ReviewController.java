package com.hunter.api;

import com.hunter.model.ChecklistRule;
import com.hunter.model.DemoUser;
import com.hunter.repository.ChecklistRuleRepository;
import com.hunter.repository.DemoUserRepository;
import com.hunter.repository.ReviewRepository;
import com.hunter.service.AzureIntegrationService;
import com.hunter.service.LocalOcrService;
import com.hunter.service.OpenFoodFactsService;
import com.hunter.service.ReviewService;
import com.hunter.service.UploadStorageService;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewRepository reviewRepository;
    private final ChecklistRuleRepository checklistRules;
    private final DemoUserRepository users;
    private final UploadStorageService storage;
    private final AzureIntegrationService azure;
    private final LocalOcrService localOcr;
    private final OpenFoodFactsService productLookup;

    public ReviewController(ReviewService reviewService, ReviewRepository reviewRepository,
                            ChecklistRuleRepository checklistRules, DemoUserRepository users,
                            UploadStorageService storage, AzureIntegrationService azure,
                            LocalOcrService localOcr, OpenFoodFactsService productLookup) {
        this.reviewService = reviewService;
        this.reviewRepository = reviewRepository;
        this.checklistRules = checklistRules;
        this.users = users;
        this.storage = storage;
        this.azure = azure;
        this.localOcr = localOcr;
        this.productLookup = productLookup;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("ok", "HUNTER local demo API");
    }

    @GetMapping("/capabilities")
    public ApiModels.CapabilitiesResponse capabilities() {
        boolean aiConfigured = azure.isOpenAiConfigured();
        return new ApiModels.CapabilitiesResponse(
                "LOCAL_CONTROLLED_FILES",
                localOcr.capability(),
                aiConfigured ? "CONFIGURED" : "NEEDS_CONFIGURATION",
                aiConfigured,
                true,
                "Local sessions and review ownership checks are enabled. Use HTTPS and secure production storage before deployment."
        );
    }

    @GetMapping("/rules")
    public List<ApiModels.RuleResponse> rules() {
        return checklistRules.findByEnabledTrueOrderBySortOrderAsc().stream()
                .map(ReviewController::toRuleResponse)
                .toList();
    }

    @GetMapping("/reviews")
    public List<ApiModels.ReviewResponse> reviews(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            Authentication authentication
    ) {
        return reviewService.list(search, status, currentUser(authentication));
    }

    @PostMapping(value = "/reviews", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiModels.ReviewResponse> createReview(
            @RequestPart("image") MultipartFile image,
            @RequestParam String productName,
            @RequestParam String category,
            @RequestParam String labelText,
            @RequestParam(defaultValue = "") String approvedFacts,
            Authentication authentication
    ) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.create(image, productName, category, labelText, approvedFacts, currentUser(authentication)));
    }

    @PostMapping(value = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiModels.OcrResponse extractText(@RequestPart("image") MultipartFile image) throws IOException {
        UploadStorageService.ValidatedImage validated = storage.validateForProcessing(image);
        return new ApiModels.OcrResponse(
                localOcr.extractText(validated),
                "Tesseract OCR (local)",
                "Text was processed locally. OCR can make mistakes; correct it against the package before review."
        );
    }

    @GetMapping("/products/barcode/{barcode}")
    public OpenFoodFactsService.ProductDetails lookupBarcode(@PathVariable String barcode) {
        return productLookup.lookup(barcode);
    }

    @PostMapping("/ai/suggestions")
    public ApiModels.AiSuggestionResponse suggestReviewNotes(
            @Valid @RequestBody ApiModels.AiSuggestionRequest request
    ) {
        return new ApiModels.AiSuggestionResponse(
                azure.suggestReviewNotes(request),
                "Azure OpenAI",
                "Optional AI-generated reviewer notes only; not a legal or compliance determination."
        );
    }

    @GetMapping("/reviews/{id}")
    public ApiModels.ReviewResponse review(@PathVariable String id, Authentication authentication) {
        return reviewService.get(id, currentUser(authentication));
    }

    @GetMapping("/reviews/{id}/image")
    public ResponseEntity<ByteArrayResource> image(@PathVariable String id, Authentication authentication) throws IOException {
        var review = reviewRepository.findByIdAndOwner_Id(id, currentUser(authentication).getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found."));
        var imagePath = storage.resolveStorageKey(review.getImageStorageKey());
        if (!Files.isRegularFile(imagePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found.");
        }
        byte[] content = Files.readAllBytes(imagePath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(review.getImageMediaType()))
                .contentLength(content.length)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(review.getOriginalImageName(), StandardCharsets.UTF_8).build().toString())
                .body(new ByteArrayResource(content));
    }

    @PostMapping("/reviews/{id}/decisions")
    public ApiModels.ReviewResponse decide(
            @PathVariable String id,
            @Valid @RequestBody ApiModels.DecisionRequest request,
            Authentication authentication
    ) {
        return reviewService.decide(id, request, currentUser(authentication));
    }

    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable String id, Authentication authentication) {
        reviewService.delete(id, currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    private DemoUser currentUser(Authentication authentication) {
        return users.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated account not found."));
    }

    private static ApiModels.RuleResponse toRuleResponse(ChecklistRule rule) {
        return new ApiModels.RuleResponse(
                rule.getCode(),
                rule.getVersion(),
                rule.getCategory(),
                rule.getTitle(),
                rule.getPrompt(),
                rule.getSourceStatus(),
                rule.getSourceReference()
        );
    }

    public record HealthResponse(String status, String service) {
    }
}
