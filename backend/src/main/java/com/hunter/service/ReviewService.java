package com.hunter.service;

import com.hunter.api.ApiModels;
import com.hunter.model.ChecklistRule;
import com.hunter.model.DemoUser;
import com.hunter.model.Review;
import com.hunter.model.ReviewDecision;
import com.hunter.model.ReviewFinding;
import com.hunter.repository.ChecklistRuleRepository;
import com.hunter.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Service
public class ReviewService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReviewService.class);

    public static final String CHECKLIST_VERSION = "demo-v0.1";
    public static final String DEFAULT_CATEGORY = "packaged-snacks";
    private final ReviewRepository reviews;
    private final ChecklistRuleRepository rules;
    private final UploadStorageService storage;
    private final ReviewRulesService reviewRules;

    public ReviewService(ReviewRepository reviews, ChecklistRuleRepository rules,
                         UploadStorageService storage, ReviewRulesService reviewRules) {
        this.reviews = reviews;
        this.rules = rules;
        this.storage = storage;
        this.reviewRules = reviewRules;
    }

    @Transactional(readOnly = true)
    public List<ApiModels.ReviewResponse> list(String search, String status, DemoUser owner) {
        String normalizedSearch = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        String normalizedStatus = status == null ? "" : status.strip().toUpperCase(Locale.ROOT);
        return reviews.findAllByOwner_IdOrderByCreatedAtDesc(owner.getId()).stream()
                .filter(review -> normalizedSearch.isEmpty()
                        || review.getProductName().toLowerCase(Locale.ROOT).contains(normalizedSearch)
                        || review.getCategory().toLowerCase(Locale.ROOT).contains(normalizedSearch))
                .filter(review -> normalizedStatus.isEmpty() || review.getStatus().equals(normalizedStatus))
                .map(ReviewService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApiModels.ReviewResponse get(String id, DemoUser owner) {
        return toResponse(findDetailed(id, owner));
    }

    @Transactional
    public ApiModels.ReviewResponse create(MultipartFile image, String productName, String category,
                                           String labelText, String approvedFacts, DemoUser owner) throws java.io.IOException {
        String normalizedProductName = normalizeRequired(productName, "Enter a product name.", 120);
        String normalizedCategory = normalizeRequired(category, "Choose a product category.", 60);
        if (!DEFAULT_CATEGORY.equals(normalizedCategory)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This MVP supports only the packaged-snacks demo category.");
        }
        String normalizedLabelText = normalizeRequired(labelText, "Enter or correct the label text before checking.", 30000);
        String normalizedApprovedFacts = normalizeOptional(approvedFacts, 30000);

        List<ChecklistRule> activeRules = rules.findByEnabledTrueAndCategoryInOrderBySortOrderAsc(
                List.of(normalizedCategory, "all-categories")
        );
        if (activeRules.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No checklist version is available for this category.");
        }
        String version = activeRules.stream().map(ChecklistRule::getVersion).max(String::compareTo).orElseThrow();
        List<ChecklistRule> selectedRules = activeRules.stream().filter(rule -> rule.getVersion().equals(version)).toList();

        UploadStorageService.StoredImage storedImage = storage.store(image);
        try {
            Instant now = Instant.now();
            Review review = new Review(
                    normalizedProductName,
                    normalizedCategory,
                    storedImage.originalName(),
                    storedImage.storageKey(),
                    storedImage.mediaType(),
                    normalizedLabelText,
                    normalizedApprovedFacts,
                    version,
                    owner,
                    now
            );
            reviewRules.evaluate(review, selectedRules);
            return toResponse(reviews.save(review));
        } catch (RuntimeException error) {
            try {
                storage.delete(storedImage.storageKey());
            } catch (java.io.IOException cleanupError) {
                error.addSuppressed(cleanupError);
            }
            throw error;
        }
    }

    @Transactional
    public ApiModels.ReviewResponse decide(String id, ApiModels.DecisionRequest request, DemoUser owner) {
        if (request == null || request.decision() == null || request.decision().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reviewer decision is required. Choose APPROVED or NEEDS_CHANGES.");
        }
        String decision = request.decision().strip().toUpperCase(Locale.ROOT);
        if (!List.of("APPROVED", "NEEDS_CHANGES").contains(decision)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose APPROVED or NEEDS_CHANGES.");
        }
        Review review = findDetailed(id, owner);
        review.addDecision(new ReviewDecision(
                review,
                owner,
                decision,
                owner.getDisplayName(),
                review.getChecklistVersion(),
                normalizeOptional(request.reason(), 2000),
                Instant.now()
        ));
        return toResponse(reviews.save(review));
    }

    @Transactional
    public String delete(String id, DemoUser owner) {
        Review review = findDetailed(id, owner);
        String storageKey = review.getImageStorageKey();
        reviews.delete(review);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    try {
                        storage.delete(storageKey);
                    } catch (IOException cleanupError) {
                        LOGGER.warn("Could not remove review image {} after commit: {}",
                                storageKey, cleanupError.getMessage());
                    }
                }
            }
        });
        return review.getProductName();
    }

    private Review findDetailed(String id, DemoUser owner) {
        if (id == null || !id.matches("[a-f0-9-]{36}")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found.");
        }
        return reviews.findDetailedByIdAndOwner_Id(id, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found."));
    }

    public static ApiModels.ReviewResponse toResponse(Review review) {
        return new ApiModels.ReviewResponse(
                review.getId(),
                review.getProductName(),
                review.getCategory(),
                review.getOriginalImageName(),
                "/api/reviews/" + review.getId() + "/image",
                review.getImageMediaType(),
                review.getChecklistVersion(),
                review.getStatus(),
                review.getOwner().getDisplayName(),
                review.getLabelText(),
                review.getApprovedFacts(),
                review.getCreatedAt(),
                review.getUpdatedAt(),
                review.getFindings().stream().map(ReviewService::toFinding).toList(),
                review.getDecisions().stream().map(ReviewService::toDecision).toList()
        );
    }

    private static ApiModels.FindingResponse toFinding(ReviewFinding finding) {
        return new ApiModels.FindingResponse(
                finding.getId(),
                finding.getRuleCode(),
                finding.getChecklistVersion(),
                finding.getTitle(),
                finding.getDetail(),
                finding.getResultLabel(),
                finding.isAttentionRequired()
        );
    }

    private static ApiModels.DecisionResponse toDecision(ReviewDecision decision) {
        return new ApiModels.DecisionResponse(
                decision.getId(),
                decision.getDecision(),
                decision.getReviewerName(),
                decision.getChecklistVersion(),
                decision.getReason(),
                decision.getCreatedAt()
        );
    }

    private static String normalizeRequired(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One of the review fields exceeds its supported length.");
        }
        return normalized;
    }

    private static String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One of the review fields exceeds its supported length.");
        }
        return normalized;
    }
}
