package com.expensetracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.expensetracker.entity.Transaction;

/**
 * Pure unit tests for the recurring-detection core (no Spring context, no
 * database) - {@link RecurringDetectionServiceImpl#detectSeries} and its
 * helpers are package-private static methods precisely so this is possible.
 */
class RecurringDetectionServiceImplTest {

    private static Transaction txn(String merchant, double amount, LocalDateTime date) {
        Transaction t = new Transaction();
        t.setMerchant(merchant);
        t.setAmount(amount);
        t.setTransactionDate(date);
        return t;
    }

    @Test
    void twoMonthlyPaymentsAreDetectedAsRecurring() {
        List<Transaction> txns = List.of(
                txn("Netflix", 649, LocalDateTime.of(2026, 7, 5, 10, 0)),
                txn("Netflix", 649, LocalDateTime.of(2026, 8, 4, 10, 0)));

        List<RecurringDetectionServiceImpl.Series> series = RecurringDetectionServiceImpl.detectSeries(txns);

        assertEquals(1, series.size());
        assertEquals("Netflix", series.get(0).merchant);
        assertEquals(649.0, series.get(0).typicalAmount, 0.01);
        assertEquals(2, series.get(0).members.size());
    }

    @Test
    void twoPaymentsAWeekApartAreDetectedAsWeekly() {
        List<Transaction> txns = List.of(
                txn("Gym", 200, LocalDateTime.of(2026, 8, 1, 8, 0)),
                txn("Gym", 200, LocalDateTime.of(2026, 8, 8, 8, 0)));

        List<RecurringDetectionServiceImpl.Series> series = RecurringDetectionServiceImpl.detectSeries(txns);

        assertEquals(1, series.size());
        assertEquals("weekly", RecurringDetectionServiceImpl.cadenceLabel(series.get(0).intervalDays));
    }

    @Test
    void twoPaymentsElevenDaysApartAreNotRecurring() {
        // Neither a clean weekly (6-9d) nor monthly (24-38d) gap for a 2-hit series.
        List<Transaction> txns = List.of(
                txn("Random Shop", 300, LocalDateTime.of(2026, 8, 1, 8, 0)),
                txn("Random Shop", 300, LocalDateTime.of(2026, 8, 12, 8, 0)));

        assertTrue(RecurringDetectionServiceImpl.detectSeries(txns).isEmpty());
    }

    @Test
    void singleTransactionIsNeverRecurring() {
        List<Transaction> txns = List.of(txn("Rent", 8000, LocalDateTime.now()));
        assertTrue(RecurringDetectionServiceImpl.detectSeries(txns).isEmpty());
    }

    @Test
    void differentMerchantsAreNotGroupedTogether() {
        List<Transaction> txns = List.of(
                txn("Netflix", 649, LocalDateTime.of(2026, 7, 5, 10, 0)),
                txn("Spotify", 649, LocalDateTime.of(2026, 8, 4, 10, 0)));

        assertTrue(RecurringDetectionServiceImpl.detectSeries(txns).isEmpty());
    }

    @Test
    void wildlyDifferentAmountsForSameMerchantAreNotGrouped() {
        List<Transaction> txns = List.of(
                txn("Amazon", 200, LocalDateTime.of(2026, 7, 5, 10, 0)),
                txn("Amazon", 5000, LocalDateTime.of(2026, 8, 4, 10, 0)));

        assertTrue(RecurringDetectionServiceImpl.detectSeries(txns).isEmpty());
    }

    @Test
    void threeRegularMonthlyPaymentsFormOneSeriesWithMedianInterval() {
        List<Transaction> txns = List.of(
                txn("Rent", 8000, LocalDateTime.of(2026, 6, 1, 9, 0)),
                txn("Rent", 8000, LocalDateTime.of(2026, 7, 1, 9, 0)),
                txn("Rent", 8000, LocalDateTime.of(2026, 8, 1, 9, 0)));

        List<RecurringDetectionServiceImpl.Series> series = RecurringDetectionServiceImpl.detectSeries(txns);

        assertEquals(1, series.size());
        assertEquals(3, series.get(0).members.size());
        assertEquals("monthly", RecurringDetectionServiceImpl.cadenceLabel(series.get(0).intervalDays));
    }

    @Test
    void oneIrregularGapAmongThreeBreaksRegularity() {
        // Gaps of 30 then 0 days: highly irregular for a 3+ hit series (this is
        // exactly what happens when a genuine monthly payment collides with an
        // unrelated same-day, same-amount transaction).
        List<Transaction> txns = List.of(
                txn("Swiggy", 799, LocalDateTime.of(2026, 8, 12, 20, 0)),
                txn("Swiggy", 799, LocalDateTime.of(2026, 9, 11, 22, 0)),
                txn("Swiggy", 799, LocalDateTime.of(2026, 9, 11, 23, 0)));

        assertTrue(RecurringDetectionServiceImpl.detectSeries(txns).isEmpty());
    }

    @Test
    void isRegularAcceptsTwoHitMonthlyAndWeeklyWindowsOnly() {
        assertTrue(RecurringDetectionServiceImpl.isRegular(List.of(30L), 2));
        assertTrue(RecurringDetectionServiceImpl.isRegular(List.of(7L), 2));
        assertFalse(RecurringDetectionServiceImpl.isRegular(List.of(15L), 2));
        assertFalse(RecurringDetectionServiceImpl.isRegular(List.of(45L), 2));
    }

    @Test
    void merchantNormalizationIsCaseAndWhitespaceInsensitive() {
        assertEquals("swiggy", RecurringDetectionServiceImpl.normalizeMerchant("  SWIGGY  "));
        assertEquals("prime video", RecurringDetectionServiceImpl.normalizeMerchant("Prime   Video"));
        assertEquals("", RecurringDetectionServiceImpl.normalizeMerchant(null));
    }

    @Test
    void medianHandlesEvenAndOddLengthArrays() {
        assertEquals(2.0, RecurringDetectionServiceImpl.median(new double[]{1, 2, 3}), 0.0001);
        assertEquals(2.5, RecurringDetectionServiceImpl.median(new double[]{1, 2, 3, 4}), 0.0001);
        assertEquals(0.0, RecurringDetectionServiceImpl.median(new double[]{}), 0.0001);
    }

    @Test
    void transactionsWithoutAMerchantAreIgnored() {
        List<Transaction> txns = new ArrayList<>(Arrays.asList(
                txn(null, 100, LocalDateTime.now()),
                txn("", 100, LocalDateTime.now().minusDays(7))));
        assertTrue(RecurringDetectionServiceImpl.detectSeries(txns).isEmpty());
    }
}
