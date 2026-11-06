package com.hunter.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ApiModels {

    private ApiModels() {
    }

    public record UserResponse(String id, String username, String displayName, String role, String workspaceName) {
    }

    public record RuleResponse(String code, String version, String category, String title, String prompt,
                               String sourceStatus, String sourceReference) {
    }

    public record FindingResponse(String id, String ruleCode, String checklistVersion, String title,
                                  String detail, String resultLabel, boolean attentionRequired) {
    }

    public record DecisionResponse(String id, String decision, String reviewerName, String checklistVersion,
                                   String reason, Instant createdAt) {
    }

    public record ReviewResponse(
            String id,
            String productName,
            String category,
            String originalImageName,
            String imageUrl,
            String imageMediaType,
            String checklistVersion,
            String status,
            String ownerName,
            String labelText,
            String approvedFacts,
            Instant createdAt,
            Instant updatedAt,
            List<FindingResponse> findings,
            List<DecisionResponse> decisions
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DecisionRequest(
            @NotBlank @Size(max = 32) String decision,
            @Size(max = 2000) String reason
    ) {
    }

    public record OcrResponse(String text, String provider, String notice) {
    }

    public record AiSuggestionRequest(
            @NotBlank @Size(max = 30000) String labelText,
            @Size(max = 30000) String approvedFacts,
            @Size(max = 12) List<@NotBlank @Size(max = 3000) String> findings
    ) {
    }

    public record AiSuggestionResponse(String text, String provider, String notice) {
    }

    public record OcrCapability(String status, String message) {
    }

    public record CapabilitiesResponse(
            String storage,
            OcrCapability ocr,
            String ai,
            boolean externalTransfer,
            boolean authentication,
            String deploymentBoundary
    ) {
    }
}
