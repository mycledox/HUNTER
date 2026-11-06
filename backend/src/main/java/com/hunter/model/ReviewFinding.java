package com.hunter.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "review_findings")
public class ReviewFinding {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @Column(nullable = false, length = 60)
    private String ruleCode;

    @Column(nullable = false, length = 32)
    private String checklistVersion;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 1000)
    private String detail;

    @Column(nullable = false, length = 40)
    private String resultLabel;

    @Column(nullable = false)
    private boolean attentionRequired;

    @Column(nullable = false)
    private int sortOrder;

    protected ReviewFinding() {
    }

    public ReviewFinding(Review review, String ruleCode, String checklistVersion, String title, String detail,
                         String resultLabel, boolean attentionRequired, int sortOrder) {
        this.id = UUID.randomUUID().toString();
        this.review = review;
        this.ruleCode = ruleCode;
        this.checklistVersion = checklistVersion;
        this.title = title;
        this.detail = detail;
        this.resultLabel = resultLabel;
        this.attentionRequired = attentionRequired;
        this.sortOrder = sortOrder;
    }

    public String getId() { return id; }
    public String getRuleCode() { return ruleCode; }
    public String getChecklistVersion() { return checklistVersion; }
    public String getTitle() { return title; }
    public String getDetail() { return detail; }
    public String getResultLabel() { return resultLabel; }
    public boolean isAttentionRequired() { return attentionRequired; }
    public int getSortOrder() { return sortOrder; }
}
