package com.hunter.service;

import com.hunter.model.ChecklistRule;
import com.hunter.model.DemoUser;
import com.hunter.model.Review;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewRulesServiceTest {

    private final ReviewRulesService service = new ReviewRulesService();

    @Test
    void reportsMissingFieldCuesAsPromptsRatherThanLegalFindings() {
        Review review = review("Potato chips, ingredients: potato and salt, net weight 52 g.");
        ChecklistRule identity = rule("PRODUCT_IDENTITY", "Product identity", "\\b(chips|snack)\\b", 0);
        ChecklistRule nutrition = rule("NUTRITION_INFORMATION", "Nutrition information", "\\b(nutrition|energy)\\b", 1);

        service.evaluate(review, List.of(identity, nutrition));

        assertThat(review.getFindings()).hasSize(2);
        assertThat(review.getFindings().get(0).getResultLabel()).isEqualTo("Label cue found");
        assertThat(review.getFindings().get(0).isAttentionRequired()).isFalse();
        assertThat(review.getFindings().get(1).getResultLabel()).isEqualTo("Check manually");
        assertThat(review.getFindings().get(1).isAttentionRequired()).isTrue();
        assertThat(review.getFindings().get(1).getDetail()).contains("not spotted");
    }

    @Test
    void comparesExampleClaimsWithEnteredFactsWithoutDeclaringCompliance() {
        Review review = review("100% natural potato chips");
        ChecklistRule claims = new ChecklistRule(
                "claims-demo-v0.1", "ON_PACK_CLAIMS", "demo-v0.1", "packaged-snacks",
                "On-pack claims", "Compare claims with product facts.",
                "CLAIM_FACTS", "", 0, true, "UNVERIFIED_DEMO", null
        );

        service.evaluate(review, List.of(claims));

        assertThat(review.getFindings()).singleElement().satisfies(finding -> {
            assertThat(finding.getResultLabel()).isEqualTo("Verify with reviewer");
            assertThat(finding.isAttentionRequired()).isTrue();
            assertThat(finding.getDetail()).contains("does not establish whether a claim is valid");
        });
    }

    @Test
    void marksMatchingClaimAsNeedingContextualHumanReview() {
        Review review = new Review(
                "Chips", "packaged-snacks", "label.png", "01234567-89ab-cdef-0123-456789abcdef.png",
                "image/png", "100% natural potato chips", "100% natural potato chips",
                "demo-v0.1", reviewer(), Instant.now()
        );
        ChecklistRule claims = new ChecklistRule(
                "claims-demo-v0.1", "ON_PACK_CLAIMS", "demo-v0.1", "packaged-snacks",
                "On-pack claims", "Compare claims with product facts.",
                "CLAIM_FACTS", "", 0, true, "UNVERIFIED_DEMO", null
        );

        service.evaluate(review, List.of(claims));

        assertThat(review.getFindings()).singleElement().satisfies(finding -> {
            assertThat(finding.getResultLabel()).isEqualTo("Compare facts");
            assertThat(finding.isAttentionRequired()).isFalse();
            assertThat(finding.getDetail()).contains("reviewer still needs to confirm");
        });
    }

    private static Review review(String labelText) {
        return new Review(
                "Potato chips", "packaged-snacks", "label.png", "01234567-89ab-cdef-0123-456789abcdef.png",
                "image/png", labelText, "", "demo-v0.1", reviewer(), Instant.now()
        );
    }

    private static DemoUser reviewer() {
        return new DemoUser("demo-reviewer", "demo-reviewer", "Demo Reviewer", "DEMO_REVIEWER", "HUNTER demo", Instant.now());
    }

    private static ChecklistRule rule(String code, String title, String textPattern, int order) {
        return new ChecklistRule(
                code + "-demo-v0.1", code, "demo-v0.1", "packaged-snacks",
                title, "Verify this field against the approved product facts.",
                "FIELD_CUE", textPattern, order, true, "UNVERIFIED_DEMO", null
        );
    }
}
