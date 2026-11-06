package com.hunter.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "label_reviews", uniqueConstraints = {
        @UniqueConstraint(name = "uk_review_image_key", columnNames = "image_storage_key")
})
public class Review {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 120)
    private String productName;

    @Column(nullable = false, length = 60)
    private String category;

    @Column(nullable = false, length = 120)
    private String originalImageName;

    @Column(nullable = false, length = 100)
    private String imageStorageKey;

    @Column(nullable = false, length = 40)
    private String imageMediaType;

    @Column(nullable = false, length = 30000)
    private String labelText;

    @Column(nullable = false, length = 30000)
    private String approvedFacts;

    @Column(nullable = false, length = 32)
    private String checklistVersion;

    @Column(nullable = false, length = 40)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private DemoUser owner;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ReviewFinding> findings = new ArrayList<>();

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    private List<ReviewDecision> decisions = new ArrayList<>();

    protected Review() {
    }

    public Review(String productName, String category, String originalImageName, String imageStorageKey,
                  String imageMediaType, String labelText, String approvedFacts, String checklistVersion,
                  DemoUser owner, Instant now) {
        this.id = UUID.randomUUID().toString();
        this.productName = productName;
        this.category = category;
        this.originalImageName = originalImageName;
        this.imageStorageKey = imageStorageKey;
        this.imageMediaType = imageMediaType;
        this.labelText = labelText;
        this.approvedFacts = approvedFacts;
        this.checklistVersion = checklistVersion;
        this.status = "NEEDS_HUMAN_REVIEW";
        this.owner = owner;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void addFinding(ReviewFinding finding) {
        findings.add(finding);
    }

    public void addDecision(ReviewDecision decision) {
        decisions.add(decision);
        status = decision.getDecision();
        updatedAt = decision.getCreatedAt();
    }

    public String getId() { return id; }
    public String getProductName() { return productName; }
    public String getCategory() { return category; }
    public String getOriginalImageName() { return originalImageName; }
    public String getImageStorageKey() { return imageStorageKey; }
    public String getImageMediaType() { return imageMediaType; }
    public String getLabelText() { return labelText; }
    public String getApprovedFacts() { return approvedFacts; }
    public String getChecklistVersion() { return checklistVersion; }
    public String getStatus() { return status; }
    public DemoUser getOwner() { return owner; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ReviewFinding> getFindings() { return findings; }
    public List<ReviewDecision> getDecisions() { return decisions; }
}
