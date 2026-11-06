package com.hunter.config;

import com.hunter.model.ChecklistRule;
import com.hunter.model.DemoUser;
import com.hunter.repository.ChecklistRuleRepository;
import com.hunter.repository.DemoUserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.List;

@Configuration
public class DemoDataConfiguration {

    private static final String VERSION = "demo-v0.1";
    private static final String CATEGORY = "packaged-snacks";

    @Bean
    ApplicationRunner seedDemoData(DemoUserRepository users, ChecklistRuleRepository rules) {
        return arguments -> {
            users.findByUsername("demo-reviewer").orElseGet(() ->
                    users.save(new DemoUser(
                            "demo-reviewer",
                            "demo-reviewer",
                            "Demo Reviewer",
                            "DEMO_REVIEWER",
                            "HUNTER demo",
                            Instant.now()
                    )));

            List<RuleSeed> seeds = List.of(
                    new RuleSeed("PRODUCT_IDENTITY", "Product identity",
                            "Can the product name be found and read clearly?",
                            "FIELD_CUE", "\\b(product|milk|chips|noodles|biscuit|snack)\\b"),
                    new RuleSeed("INGREDIENT_INFORMATION", "Ingredient information",
                            "Check the ingredient and allergen text against the approved product facts.",
                            "FIELD_CUE", "\\b(ingredients?|contains|allergens?)\\b"),
                    new RuleSeed("NUTRITION_INFORMATION", "Nutrition information",
                            "Review the nutrition panel and its legibility.",
                            "FIELD_CUE", "\\b(nutrition|energy|protein|carbohydrate|total fat|per\\s*100)\\b"),
                    new RuleSeed("NET_QUANTITY", "Net quantity",
                            "Confirm the declared quantity against the product record.",
                            "FIELD_CUE", "\\bnet\\s*(weight|quantity|qty)|\\b\\d+(?:\\.\\d+)?\\s?(?:g|kg|ml|l)\\b"),
                    new RuleSeed("BUSINESS_DETAILS", "Business details",
                            "Look for manufacturer or marketer details to verify.",
                            "FIELD_CUE", "\\b(manufactured|manufacturer|marketed by|packed by|customer care|address)\\b"),
                    new RuleSeed("ON_PACK_CLAIMS", "On-pack claims",
                            "Compare claims with approved product facts; uncertain wording needs a person.",
                            "CLAIM_FACTS", "")
            );

            for (int index = 0; index < seeds.size(); index++) {
                RuleSeed seed = seeds.get(index);
                if (!rules.existsByCodeAndVersion(seed.code(), VERSION)) {
                    rules.save(new ChecklistRule(
                            seed.code() + "-" + VERSION,
                            seed.code(),
                            VERSION,
                            CATEGORY,
                            seed.title(),
                            seed.prompt(),
                            seed.checkType(),
                            seed.textPattern(),
                            index,
                            true,
                            "UNVERIFIED_DEMO",
                            null
                    ));
                }
            }
        };
    }

    private record RuleSeed(String code, String title, String prompt, String checkType, String textPattern) {
    }
}
