package com.expensetracker.service;

import com.expensetracker.dto.CategorySuggestion;
import com.expensetracker.entity.User;

/**
 * Deterministic, rule-based auto-categorization for incoming transactions.
 *
 * <p>No machine learning, no external API: it walks the user's applicable
 * {@link com.expensetracker.entity.CategoryRule}s (personal first, then
 * built-in defaults, each set ordered by priority) and returns the first
 * match. That makes every decision cheap, offline, and fully explainable in
 * the review inbox ("matched rule 'swiggy' -> Food").
 */
public interface CategorizationService {

    /**
     * @param merchant     the transaction's merchant, may be null
     * @param description  the transaction's description/narration, may be null
     * @return the best category suggestion, or {@link CategorySuggestion#none()}
     *         if nothing matched. Never returns null.
     */
    CategorySuggestion suggest(User user, String merchant, String description);
}
