package com.hunter.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "checklist_rules", uniqueConstraints = {
        @UniqueConstraint(name = "uk_checklist_rule_version", columnNames = {"rule_code", "version"})
})
public class ChecklistRule {

    @Id
    @Column(length = 100)
    private String id;

    @Column(name = "rule_code", nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 30)
    private String version;

    @Column(nullable = false, length = 60)
    private String category;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String prompt;

    @Column(nullable = false, length = 40)
    private String checkType;

    @Column(length = 240)
    private String textPattern;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, length = 40)
    private String sourceStatus;

    @Column(length = 1000)
    private String sourceReference;

    protected ChecklistRule() {
    }

    public ChecklistRule(String id, String code, String version, String category, String title, String prompt,
                        String checkType, String textPattern, int sortOrder, boolean enabled,
                        String sourceStatus, String sourceReference) {
        this.id = id;
        this.code = code;
        this.version = version;
        this.category = category;
        this.title = title;
        this.prompt = prompt;
        this.checkType = checkType;
        this.textPattern = textPattern;
        this.sortOrder = sortOrder;
        this.enabled = enabled;
        this.sourceStatus = sourceStatus;
        this.sourceReference = sourceReference;
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public String getVersion() { return version; }
    public String getCategory() { return category; }
    public String getTitle() { return title; }
    public String getPrompt() { return prompt; }
    public String getCheckType() { return checkType; }
    public String getTextPattern() { return textPattern; }
    public int getSortOrder() { return sortOrder; }
    public boolean isEnabled() { return enabled; }
    public String getSourceStatus() { return sourceStatus; }
    public String getSourceReference() { return sourceReference; }
}
