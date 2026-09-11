package com.expensetracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for the fuzzy-matching helpers behind duplicate detection
 * (no Spring context, no database). {@code checkAgainstExpenses} /
 * {@code findSimilarTransaction} themselves are exercised end-to-end in
 * manual/browser testing, since they need a real repository.
 */
class DuplicateDetectionServiceImplTest {

    private final DuplicateDetectionServiceImpl service = new DuplicateDetectionServiceImpl(null, null);

    @Test
    void amountsWithinOnePercentOrOneRupeeMatch() {
        assertTrue(service.amountsMatch(100.0, 100.5));
        assertTrue(service.amountsMatch(1000.0, 1009.0)); // within 1%
        assertFalse(service.amountsMatch(1000.0, 1050.0)); // 5% apart
        assertFalse(service.amountsMatch(10.0, 12.0));     // small amounts: flat Rs 1 tolerance
    }

    @Test
    void wordsExtractsLowercasedTokensAndDropsNoiseAndShortWords() {
        Set<String> words = DuplicateDetectionServiceImpl.words("UPI/Swiggy Order/Food", "Dinner at restaurant");
        assertTrue(words.contains("swiggy"));
        assertTrue(words.contains("order"));
        assertTrue(words.contains("dinner"));
        assertFalse(words.contains("upi"));   // noise word
        assertFalse(words.contains("at"));    // too short
    }

    @Test
    void wordsHandlesNullPartsGracefully() {
        assertTrue(DuplicateDetectionServiceImpl.words((String) null).isEmpty());
        assertTrue(DuplicateDetectionServiceImpl.words(null, "Swiggy").contains("swiggy"));
    }

    @Test
    void intersectionSizeCountsSharedWords() {
        Set<String> a = Set.of("swiggy", "order", "food");
        Set<String> b = Set.of("swiggy", "dinner");
        assertEquals(1, DuplicateDetectionServiceImpl.intersectionSize(a, b));
        assertEquals(0, DuplicateDetectionServiceImpl.intersectionSize(Set.of(), b));
        assertEquals(0, DuplicateDetectionServiceImpl.intersectionSize(a, Set.of()));
    }
}
