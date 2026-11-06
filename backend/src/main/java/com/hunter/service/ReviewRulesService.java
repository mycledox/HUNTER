package com.hunter.service;

import com.hunter.model.ChecklistRule;
import com.hunter.model.Review;
import com.hunter.model.ReviewFinding;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ReviewRulesService {

    private static final Pattern CLAIM_PATTERN = Pattern.compile(
            "\\b(100%\\s*(?:natural|pure)|sugar[\\s-]?free|natural|healthy|organic)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public void evaluate(Review review, List<ChecklistRule> rules) {
        String label = review.getLabelText().toLowerCase(Locale.ROOT);
        String facts = review.getApprovedFacts().toLowerCase(Locale.ROOT);
        for (int index = 0; index < rules.size(); index++) {
            ChecklistRule rule = rules.get(index);
            if ("CLAIM_FACTS".equals(rule.getCheckType())) {
                addClaimFinding(review, rule, label, facts, index);
            } else {
                addFieldFinding(review, rule, label, index);
            }
        }
    }

    private static void addFieldFinding(Review review, ChecklistRule rule, String label, int index) {
        boolean found = Pattern.compile(rule.getTextPattern(), Pattern.CASE_INSENSITIVE)
                .matcher(label).find();
        String detail = found
                ? "A matching word or field label appears in the entered text. " + rule.getPrompt()
                : "This field cue was not spotted in the entered text. " + rule.getPrompt();
        review.addFinding(new ReviewFinding(
                review,
                rule.getCode(),
                rule.getVersion(),
                rule.getTitle(),
                detail,
                found ? "Label cue found" : "Check manually",
                !found,
                index
        ));
    }

    private static void addClaimFinding(Review review, ChecklistRule rule, String label, String facts, int index) {
        var matcher = CLAIM_PATTERN.matcher(label);
        boolean matchedClaim = false;
        boolean unmatchedClaim = false;
        StringBuilder matchedPhrases = new StringBuilder();
        while (matcher.find()) {
            matchedClaim = true;
            String phrase = matcher.group();
            if (!facts.contains(phrase.toLowerCase(Locale.ROOT))) {
                unmatchedClaim = true;
            }
            if (!matchedPhrases.isEmpty()) {
                matchedPhrases.append(", ");
            }
            matchedPhrases.append(phrase);
        }

        String detail;
        String result;
        if (!matchedClaim) {
            detail = "No example claim phrase matched. This is not a full claim review. " + rule.getPrompt();
            result = "No demo match";
        } else if (unmatchedClaim) {
            detail = matchedPhrases + " appears in the entered text. Compare each claim with approved product facts and ask a reviewer to verify it. A text match does not establish whether a claim is valid.";
            result = "Verify with reviewer";
        } else {
            detail = matchedPhrases + " appears in the entered text and in the supplied product facts. A reviewer still needs to confirm the source and context.";
            result = "Compare facts";
        }

        review.addFinding(new ReviewFinding(
                review,
                rule.getCode(),
                rule.getVersion(),
                rule.getTitle(),
                detail,
                result,
                matchedClaim && unmatchedClaim,
                index
        ));
    }
}
