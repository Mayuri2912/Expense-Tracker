package com.expensetracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for the bank-statement parsing helpers (no Spring context,
 * no database, no HTTP) - all package-private static methods precisely so
 * this is possible. The full import pipeline (dedupe, categorization,
 * persistence) was exercised end-to-end in manual/browser testing.
 */
class StatementImportServiceImplTest {

    private final StatementImportServiceImpl service = new StatementImportServiceImpl(null, null, null, null);

    // ---- CSV line splitting ----

    @Test
    void splitsPlainCommaSeparatedLine() {
        assertEquals(List.of("01-09-2026", "Zomato order", "450.00", "ZOM1"),
                StatementImportServiceImpl.splitCsvLine("01-09-2026,Zomato order,450.00,ZOM1"));
    }

    @Test
    void handlesQuotedFieldWithEmbeddedComma() {
        List<String> fields = StatementImportServiceImpl.splitCsvLine("01-09-2026,\"Big Bazaar, Andheri\",1200");
        assertEquals("Big Bazaar, Andheri", fields.get(1));
    }

    @Test
    void handlesEscapedDoubleQuoteInsideQuotedField() {
        List<String> fields = StatementImportServiceImpl.splitCsvLine("1,\"She said \"\"hi\"\"\",3");
        assertEquals("She said \"hi\"", fields.get(1));
    }

    @Test
    void emptyTrailingFieldIsPreserved() {
        assertEquals(List.of("a", "b", ""), StatementImportServiceImpl.splitCsvLine("a,b,"));
    }

    // ---- date parsing ----

    @Test
    void parsesCommonIndianBankDateFormats() {
        assertEquals(LocalDate.of(2026, 9, 1), StatementImportServiceImpl.parseDate("2026-09-01"));
        assertEquals(LocalDate.of(2026, 9, 1), StatementImportServiceImpl.parseDate("01/09/2026"));
        assertEquals(LocalDate.of(2026, 9, 1), StatementImportServiceImpl.parseDate("01-09-2026"));
        assertEquals(LocalDate.of(2026, 9, 1), StatementImportServiceImpl.parseDate("01-Sep-2026"));
        assertEquals(LocalDate.of(2026, 9, 1), StatementImportServiceImpl.parseDate("01 Sep 2026"));
    }

    @Test
    void unrecognisedOrBlankDateReturnsNull() {
        assertNull(StatementImportServiceImpl.parseDate("99-99-2026"));
        assertNull(StatementImportServiceImpl.parseDate("not a date"));
        assertNull(StatementImportServiceImpl.parseDate(""));
        assertNull(StatementImportServiceImpl.parseDate(null));
    }

    // ---- amount parsing ----

    @Test
    void parsesAmountsWithCommasAndCurrencySymbols() {
        assertEquals(1234.56, StatementImportServiceImpl.parseAmount("1,234.56"), 0.001);
        assertEquals(500.0, StatementImportServiceImpl.parseAmount("₹500.00"), 0.001);
        assertEquals(500.0, StatementImportServiceImpl.parseAmount("Rs. 500"), 0.001);
        assertEquals(500.0, StatementImportServiceImpl.parseAmount(" 500.00 Dr"), 0.001);
    }

    @Test
    void parenthesesMeanNegativeButStoredAmountIsAlwaysAbsolute() {
        assertEquals(1234.0, StatementImportServiceImpl.parseAmount("(1,234.00)"), 0.001);
    }

    @Test
    void blankOrUnparsableAmountReturnsNull() {
        assertNull(StatementImportServiceImpl.parseAmount(""));
        assertNull(StatementImportServiceImpl.parseAmount(null));
        assertNull(StatementImportServiceImpl.parseAmount("N/A"));
    }

    // ---- sanitization (defends against CSV/formula injection on later export) ----

    @Test
    void leadingFormulaCharacterIsNeutralised() {
        assertEquals("'=cmd|'/C calc'", StatementImportServiceImpl.sanitize("=cmd|'/C calc'"));
        assertEquals("'+1+1", StatementImportServiceImpl.sanitize("+1+1"));
        assertEquals("ordinary text", StatementImportServiceImpl.sanitize("ordinary text"));
    }

    @Test
    void sanitizeTrimsAndCapsLength() {
        String tooLong = "x".repeat(300);
        assertEquals(255, StatementImportServiceImpl.sanitize(tooLong).length());
        assertNull(StatementImportServiceImpl.sanitize("   "));
        assertNull(StatementImportServiceImpl.sanitize(null));
    }

    // ---- hashing (used to build a stable external id when no reference column exists) ----

    @Test
    void sha256HexIsDeterministicAndDistinguishesInput() {
        String a = StatementImportServiceImpl.sha256Hex("user1|2026-09-01|450.0|zomato");
        String b = StatementImportServiceImpl.sha256Hex("user1|2026-09-01|450.0|zomato");
        String c = StatementImportServiceImpl.sha256Hex("user1|2026-09-02|450.0|zomato");
        assertEquals(a, b);
        assertTrue(!a.equals(c));
        assertEquals(64, a.length());
    }

    // ---- full CSV read (header + rows -> grid) ----

    @Test
    void readCsvSkipsBlankLinesAndParsesEachRow() {
        String csv = "Date,Description,Debit\n01-09-2026,Zomato,450\n\n03-09-2026,Amazon,1299\n";
        List<List<String>> grid = service.readCsv(csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(3, grid.size()); // header + 2 data rows, blank line skipped
        assertEquals("Zomato", grid.get(1).get(1));
    }
}
