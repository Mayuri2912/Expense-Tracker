package com.expensetracker.config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.expensetracker.entity.CategoryRule;
import com.expensetracker.entity.RuleMatchType;
import com.expensetracker.repository.CategoryRuleRepository;

/**
 * Loads the built-in default categorization rules from
 * {@code src/main/resources/default-category-rules.csv} once, on startup.
 *
 * <p>Idempotent: if any global rule ({@code user_id IS NULL}) already exists,
 * this does nothing - so it is safe on every boot and never fights the copy of
 * the same rows in {@code database/expense_tracker.sql}. Any failure here is
 * logged and swallowed; the Review Hub still works, just without the shipped
 * defaults until the user adds their own rules.
 */
@Component
public class CategoryRuleSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CategoryRuleSeeder.class);
    private static final String RESOURCE = "default-category-rules.csv";

    private final CategoryRuleRepository ruleRepository;

    public CategoryRuleSeeder(CategoryRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            long existing = ruleRepository.countByUserIsNull();
            if (existing > 0) {
                log.info("Categorization rules: {} built-in rules already present, skipping seed.", existing);
                return;
            }
            List<CategoryRule> defaults = load();
            if (defaults.isEmpty()) {
                log.warn("Categorization rules: {} contained no usable rows.", RESOURCE);
                return;
            }
            ruleRepository.saveAll(defaults);
            log.info("Categorization rules: seeded {} built-in rules from {}.", defaults.size(), RESOURCE);
        } catch (Exception ex) {
            log.error("Categorization rules: could not seed defaults from {} - the Review Hub still works without them. Cause: {}",
                    RESOURCE, ex.toString());
        }
    }

    private List<CategoryRule> load() throws Exception {
        List<CategoryRule> rules = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                // Simple split is safe here: patterns never contain a comma.
                String[] parts = trimmed.split(",");
                if (parts.length < 3) {
                    log.warn("Categorization rules: skipping malformed line {} in {}: '{}'", lineNo, RESOURCE, trimmed);
                    continue;
                }
                CategoryRule rule = new CategoryRule();
                rule.setUser(null); // built-in default
                rule.setMatchType(parseMatchType(parts[0]));
                rule.setPattern(parts[1].trim().toLowerCase());
                rule.setCategoryName(parts[2].trim());
                rule.setPriority(parts.length >= 4 ? parsePriority(parts[3]) : 100);
                rule.setActive(true);
                rules.add(rule);
            }
        }
        return rules;
    }

    private RuleMatchType parseMatchType(String raw) {
        try {
            return RuleMatchType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return RuleMatchType.CONTAINS;
        }
    }

    private int parsePriority(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return 100;
        }
    }
}
