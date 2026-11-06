package com.hunter.service;

import com.hunter.api.ApiModels;
import com.hunter.model.ChecklistRule;
import com.hunter.model.DemoUser;
import com.hunter.model.Review;
import com.hunter.repository.ChecklistRuleRepository;
import com.hunter.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewServiceTest {

    private ReviewRepository reviews;
    private ChecklistRuleRepository rules;
    private UploadStorageService storage;
    private ReviewRulesService reviewRules;
    private ReviewService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        reviews = mock(ReviewRepository.class);
        rules = mock(ChecklistRuleRepository.class);
        storage = new UploadStorageService(tempDir.toString());
        reviewRules = mock(ReviewRulesService.class);
        service = new ReviewService(reviews, rules, storage, reviewRules);
    }

    private DemoUser demoUser() {
        return new DemoUser("user-1", "demo@example.test", "Demo Reviewer", "REVIEWER", "Hunter Demo", Instant.now());
    }

    private MockMultipartFile pngUpload() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return new MockMultipartFile("image", "label.png", "image/png", bytes.toByteArray());
    }

    private ChecklistRule rule(String version) {
        return new ChecklistRule("id-" + version, "R1", version, "packaged-snacks", "Title",
                "Prompt", "TEXT_CONTAINS", null, 1, true, "UNVERIFIED", null);
    }

    @Test
    void rejectsMissingProductName() throws Exception {
        assertThatThrownBy(() -> service.create(pngUpload(), "  ", "packaged-snacks", "Some text", "", demoUser()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Enter a product name");
    }

    @Test
    void rejectsUnsupportedCategory() throws Exception {
        assertThatThrownBy(() -> service.create(pngUpload(), "Chips", "beverages", "Some text", "", demoUser()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("packaged-snacks");
        verify(rules, never()).findByEnabledTrueAndCategoryInOrderBySortOrderAsc(anyList());
    }

    @Test
    void rejectsMissingLabelText() throws Exception {
        assertThatThrownBy(() -> service.create(pngUpload(), "Chips", "packaged-snacks", null, "", demoUser()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("label text");
    }

    @Test
    void rejectsFieldExceedingLength() throws Exception {
        String tooLong = "x".repeat(30001);
        assertThatThrownBy(() -> service.create(pngUpload(), "Chips", "packaged-snacks", tooLong, "", demoUser()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("exceeds its supported length");
    }

    @Test
    void selectsHighestChecklistVersionOnly() throws Exception {
        MockMultipartFile upload = pngUpload();
        when(rules.findByEnabledTrueAndCategoryInOrderBySortOrderAsc(anyList()))
                .thenReturn(List.of(rule("demo-v0.1"), rule("demo-v0.3"), rule("demo-v0.7")));
        when(reviews.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApiModels.ReviewResponse response = service.create(upload, "Chips", "packaged-snacks", "Label text", "", demoUser());

        assertThat(response.checklistVersion()).isEqualTo("demo-v0.7");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChecklistRule>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(reviewRules).evaluate(any(Review.class), captor.capture());
        assertThat(captor.getValue()).allMatch(ruleValue -> ruleValue.getVersion().equals("demo-v0.7"));
    }

    @Test
    void rejectsWhenNoRulesAvailable() throws Exception {
        when(rules.findByEnabledTrueAndCategoryInOrderBySortOrderAsc(anyList())).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(pngUpload(), "Chips", "packaged-snacks", "Text", "", demoUser()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No checklist version");
    }

    @Test
    void createsReviewForAuthenticatedOwner() throws Exception {
        when(rules.findByEnabledTrueAndCategoryInOrderBySortOrderAsc(anyList()))
                .thenReturn(List.of(rule("demo-v0.1")));
        when(reviews.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(pngUpload(), "Chips", "packaged-snacks", "Text", "", demoUser());

        verify(reviews).save(org.mockito.ArgumentMatchers.argThat(review ->
                review.getOwner().getId().equals(demoUser().getId())));
    }

    @Test
    void deletesStoredImageWhenRuleEvaluationFails() throws Exception {
        when(rules.findByEnabledTrueAndCategoryInOrderBySortOrderAsc(anyList()))
                .thenReturn(List.of(rule("demo-v0.1")));
        org.mockito.Mockito.doThrow(new IllegalStateException("rule engine failed"))
                .when(reviewRules).evaluate(any(Review.class), anyList());

        assertThatThrownBy(() -> service.create(pngUpload(), "Chips", "packaged-snacks", "Text", "", demoUser()))
                .isInstanceOf(IllegalStateException.class);

        try (var files = java.nio.file.Files.list(tempDir)) {
            assertThat(files.toList()).isEmpty();
        }
        verify(reviews, never()).save(any(Review.class));
    }

    @Test
    void decideAcceptsOnlyKnownDecisions() {
        Review review = new Review("Chips", "packaged-snacks", "label.png", "key.jpg", "image/jpeg",
                "text", "", "demo-v0.1", demoUser(), Instant.now());
        when(reviews.findDetailedByIdAndOwner_Id(anyString(), anyString())).thenReturn(java.util.Optional.of(review));

        assertThatThrownBy(() -> service.decide(review.getId(),
                new ApiModels.DecisionRequest("REJECTED", ""), demoUser()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("APPROVED or NEEDS_CHANGES");
        verify(reviews, never()).save(any(Review.class));
    }

    @Test
    void decideRecordsDecisionAndUpdatesStatus() {
        Review review = new Review("Chips", "packaged-snacks", "label.png", "key.jpg", "image/jpeg",
                "text", "", "demo-v0.1", demoUser(), Instant.now());
        when(reviews.findDetailedByIdAndOwner_Id(anyString(), anyString())).thenReturn(java.util.Optional.of(review));
        when(reviews.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApiModels.ReviewResponse response = service.decide(review.getId(),
                new ApiModels.DecisionRequest("approved", "looks fine"), demoUser());

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.decisions()).hasSize(1);
        assertThat(response.decisions().get(0).reviewerName()).isEqualTo("Demo Reviewer");
        assertThat(response.decisions().get(0).reason()).isEqualTo("looks fine");
    }

    @Test
    void decideRejectsUnknownReviewId() {
        assertThatThrownBy(() -> service.decide("not-a-uuid",
                new ApiModels.DecisionRequest("APPROVED", ""), demoUser()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Review not found");
    }

}
