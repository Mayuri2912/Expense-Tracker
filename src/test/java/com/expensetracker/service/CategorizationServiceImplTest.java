package com.expensetracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.expensetracker.dto.CategorySuggestion;
import com.expensetracker.dto.CategorySuggestion.Confidence;
import com.expensetracker.entity.Category;
import com.expensetracker.entity.CategoryRule;
import com.expensetracker.entity.RuleMatchType;
import com.expensetracker.entity.User;

/**
 * Pure unit tests for the categorization engine's matching core (no Spring
 * context, no database) - {@link CategorizationServiceImpl#match} is a
 * package-private static method precisely so this is possible.
 */
class CategorizationServiceImplTest {

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private static Category category(long id, String name, User owner) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        c.setUser(owner);
        return c;
    }

    private static CategoryRule defaultRule(String pattern, String categoryName, int priority) {
        CategoryRule r = new CategoryRule();
        r.setUser(null);
        r.setMatchType(RuleMatchType.CONTAINS);
        r.setPattern(pattern);
        r.setCategoryName(categoryName);
        r.setPriority(priority);
        r.setActive(true);
        return r;
    }

    private static CategoryRule personalRule(User owner, String pattern, Category target) {
        CategoryRule r = new CategoryRule();
        r.setUser(owner);
        r.setMatchType(RuleMatchType.CONTAINS);
        r.setPattern(pattern);
        r.setCategoryName(target.getName());
        r.setCategory(target);
        r.setPriority(50);
        r.setActive(true);
        return r;
    }

    @Test
    void matchesAndResolvesToTheUsersOwnCategoryByName() {
        User u = user(1);
        Category food = category(10, "Food", u);
        CategoryRule rule = defaultRule("swiggy", "Food", 10);

        CategorySuggestion s = CategorizationServiceImpl.match(List.of(rule), List.of(food), 1L, "SWIGGY", null);

        assertTrue(s.isMatched());
        assertTrue(s.isResolved());
        assertEquals(food, s.getCategory());
        assertEquals(Confidence.HIGH, s.getConfidence());
    }

    @Test
    void matchesButLeavesCategoryUnresolvedWhenUserHasNoSuchCategory() {
        CategoryRule rule = defaultRule("amazon", "Shopping", 10);

        CategorySuggestion s = CategorizationServiceImpl.match(List.of(rule), List.of(), 1L, "Amazon", "order");

        assertTrue(s.isMatched());
        assertFalse(s.isResolved());
        assertNull(s.getCategory());
        assertEquals("Shopping", s.getCategoryName());
        assertTrue(s.getReason().contains("no category by that name"));
    }

    @Test
    void noRuleMatchesReturnsNone() {
        CategoryRule rule = defaultRule("swiggy", "Food", 10);
        CategorySuggestion s = CategorizationServiceImpl.match(List.of(rule), List.of(), 1L, "Some Random Merchant", null);

        assertFalse(s.isMatched());
        assertEquals(Confidence.NONE, s.getConfidence());
    }

    @Test
    void personalRuleWinsOverDefaultRuleWhenListedFirst() {
        User u = user(1);
        Category food = category(10, "Food", u);
        Category snacks = category(11, "Snacks", u);

        CategoryRule personal = personalRule(u, "swiggy", snacks);
        CategoryRule fallbackDefault = defaultRule("swiggy", "Food", 10);

        // Repository order: personal rules first, then defaults by priority -
        // simulated here by list order.
        CategorySuggestion s = CategorizationServiceImpl.match(
                List.of(personal, fallbackDefault), List.of(food, snacks), 1L, "Swiggy", null);

        assertEquals(snacks, s.getCategory());
        assertEquals(Confidence.HIGH, s.getConfidence()); // personal rules are always HIGH
    }

    @Test
    void equalsMatchTypeRequiresExactText() {
        CategoryRule rule = new CategoryRule();
        rule.setMatchType(RuleMatchType.EQUALS);
        rule.setPattern("uber");
        rule.setCategoryName("Travel");
        rule.setPriority(10);

        assertFalse(CategorizationServiceImpl.match(List.of(rule), List.of(), 1L, "Uber Eats", null).isMatched());
        assertTrue(CategorizationServiceImpl.match(List.of(rule), List.of(), 1L, "Uber", null).isMatched());
    }

    @Test
    void confidenceTiersFollowPriorityForDefaultRules() {
        assertEquals(Confidence.HIGH, CategorizationServiceImpl.confidenceFor(defaultRule("x", "Y", 10)));
        assertEquals(Confidence.MEDIUM, CategorizationServiceImpl.confidenceFor(defaultRule("x", "Y", 20)));
        assertEquals(Confidence.LOW, CategorizationServiceImpl.confidenceFor(defaultRule("x", "Y", 30)));
    }

    @Test
    void blankOrMissingMerchantAndDescriptionYieldsNone() {
        CategoryRule rule = defaultRule("swiggy", "Food", 10);
        assertFalse(CategorizationServiceImpl.match(List.of(rule), List.of(), 1L, null, null).isMatched());
        assertFalse(CategorizationServiceImpl.match(List.of(rule), List.of(), 1L, "  ", "").isMatched());
    }

    @Test
    void aCategoryOwnedByAnotherUserIsNeverResolvedEvenIfDirectlyLinked() {
        User owner = user(1);
        User someoneElse = user(2);
        Category othersFood = category(99, "Food", someoneElse);

        CategoryRule ruleWithForeignCategory = new CategoryRule();
        ruleWithForeignCategory.setUser(owner);
        ruleWithForeignCategory.setMatchType(RuleMatchType.CONTAINS);
        ruleWithForeignCategory.setPattern("swiggy");
        ruleWithForeignCategory.setCategoryName("Food");
        ruleWithForeignCategory.setCategory(othersFood); // should never happen in practice, but must fail safe
        ruleWithForeignCategory.setPriority(50);

        CategorySuggestion s = CategorizationServiceImpl.match(
                List.of(ruleWithForeignCategory), List.of(), 1L, "Swiggy", null);

        assertTrue(s.isMatched());
        assertFalse(s.isResolved());
    }
}
