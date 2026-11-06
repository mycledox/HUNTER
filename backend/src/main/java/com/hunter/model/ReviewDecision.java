package com.hunter.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_decisions")
public class ReviewDecision {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private DemoUser reviewer;

    @Column(nullable = false, length = 32)
    private String decision;

    @Column(nullable = false, length = 140)
    private String reviewerName;

    @Column(nullable = false, length = 32)
    private String checklistVersion;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Column(nullable = false)
    private Instant createdAt;

    protected ReviewDecision() {
    }

    public ReviewDecision(Review review, DemoUser reviewer, String decision, String reviewerName,
                          String checklistVersion, String reason, Instant createdAt) {
        this.id = UUID.randomUUID().toString();
        this.review = review;
        this.reviewer = reviewer;
        this.decision = decision;
        this.reviewerName = reviewerName;
        this.checklistVersion = checklistVersion;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getDecision() { return decision; }
    public String getReviewerName() { return reviewerName; }
    public String getChecklistVersion() { return checklistVersion; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
