package com.expensetracker.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.expensetracker.dto.CategorySuggestion;
import com.expensetracker.dto.CategorySuggestion.Confidence;
import com.expensetracker.entity.Category;
import com.expensetracker.entity.CategoryRule;
import com.expensetracker.entity.RuleMatchType;
import com.expensetracker.entity.User;
import com.expensetracker.repository.CategoryRuleRepository;

@Service
public class CategorizationServiceImpl implements CategorizationService {

    private static final Logger log = LoggerFactory.getLogger(CategorizationServiceImpl.class);

    private final CategoryRuleRepository ruleRepository;
    private final CategoryService categoryService;

    public CategorizationServiceImpl(CategoryRuleRepository ruleRepository, CategoryService categoryService) {
        this.ruleRepository = ruleRepository;
        this.categoryService = categoryService;
    }

    @Override
    public CategorySuggestion suggest(User user, String merchant, String description) {
        // Rules already come back personal-first, then by priority (see the
        // @Query). Categories are loaded once so a name lookup is cheap.
        List<CategoryRule> rules = ruleRepository.findApplicableRules(user);
        List<Category> userCategories = categoryService.getAllCategoriesForUser(user);
        CategorySuggestion suggestion = match(rules, userCategories,
                user == null ? null : user.getId(), merchant, description);
        if (suggestion.isMatched()) {
            log.debug("Categorized merchant='{}' desc='{}' -> {}", merchant, description, suggestion.getCategoryName());
        }
        return suggestion;
    }

    // ---------------------------------------------------------------------
    // Pure matching core - no Spring, no DB - so it can be unit-tested with
    // hand-built rule/category lists.
    // ---------------------------------------------------------------------
    static CategorySuggestion match(List<CategoryRule> rules, List<Category> userCategories,
                                     Long userId, String merchant, String description) {

        String haystack = normalize((merchant == null ? "" : merchant) + " " + (description == null ? "" : description));
        if (haystack.isBlank() || rules == null || rules.isEmpty()) {
            return CategorySuggestion.none();
        }

        for (CategoryRule rule : rules) {
            String pattern = rule.getPattern() == null ? "" : rule.getPattern().trim().toLowerCase();
            if (pattern.isEmpty()) {
                continue;
            }
            boolean hit = rule.getMatchType() == RuleMatchType.EQUALS
                    ? haystack.equals(pattern)
                    : haystack.contains(pattern);
            if (!hit) {
                continue;
            }

            Category resolved = ownedCategory(rule.getCategory(), userId);
            if (resolved == null && userCategories != null) {
                resolved = userCategories.stream()
                        .filter(c -> c.getName() != null && c.getName().equalsIgnoreCase(rule.getCategoryName()))
                        .findFirst()
                        .orElse(null);
            }
            return CategorySuggestion.of(rule.getCategoryName(), resolved,
                    buildReason(rule, pattern, resolved), confidenceFor(rule));
        }
        return CategorySuggestion.none();
    }

    private static Category ownedCategory(Category category, Long userId) {
        if (category == null || category.getUser() == null || userId == null) {
            return null;
        }
        return userId.equals(category.getUser().getId()) ? category : null;
    }

    static Confidence confidenceFor(CategoryRule rule) {
        if (rule.getUser() != null) {
            return Confidence.HIGH; // the user themselves asked for this mapping
        }
        int priority = rule.getPriority() == null ? 100 : rule.getPriority();
        if (priority <= 10) {
            return Confidence.HIGH;   // a specific brand name (e.g. "swiggy")
        }
        if (priority <= 20) {
            return Confidence.MEDIUM; // a generic keyword (e.g. "restaurant")
        }
        return Confidence.LOW;
    }

    private static String buildReason(CategoryRule rule, String pattern, Category resolved) {
        String verb = rule.getMatchType() == RuleMatchType.EQUALS ? "is" : "contains";
        String lead = rule.getUser() != null
                ? "Your saved rule: text " + verb + " “" + pattern + "”"
                : "Merchant text " + verb + " “" + pattern + "”";
        String tail = resolved == null
                ? " → " + rule.getCategoryName() + " (you have no category by that name yet)"
                : " → " + resolved.getName();
        String reason = lead + tail + " · " + confidenceFor(rule).name().toLowerCase() + " confidence";
        return reason.length() > 255 ? reason.substring(0, 255) : reason;
    }

    private static String normalize(String text) {
        return text.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
