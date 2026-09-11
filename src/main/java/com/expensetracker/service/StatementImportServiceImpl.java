package com.expensetracker.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.expensetracker.dto.CategorySuggestion;
import com.expensetracker.dto.ParsedStatementRow;
import com.expensetracker.dto.StatementImportResult;
import com.expensetracker.entity.ReviewStatus;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.TransactionStatus;
import com.expensetracker.entity.User;
import com.expensetracker.repository.TransactionRepository;

@Service
public class StatementImportServiceImpl implements StatementImportService {

    private static final Logger log = LoggerFactory.getLogger(StatementImportServiceImpl.class);

    private static final long MAX_BYTES = 2L * 1024 * 1024;   // 2 MB
    private static final int MAX_ROWS = 500;
    private static final int MAX_FIELD_LEN = 255;

    // Header keywords, checked as case-insensitive substrings.
    private static final String[] DATE_KEYS = {"txn date", "transaction date", "value date", "date"};
    private static final String[] DEBIT_KEYS = {"withdrawal", "debit", "dr amount", "dr", "paid out", "spent"};
    private static final String[] AMOUNT_KEYS = {"amount"};
    private static final String[] CREDIT_KEYS = {"deposit", "credit", "cr amount", "paid in"};
    private static final String[] DESC_KEYS = {"narration", "description", "particulars", "details", "remarks", "merchant", "payee"};
    private static final String[] REF_KEYS = {"reference", "ref no", "ref", "utr", "cheque", "chq", "transaction id", "txn id"};
    private static final String[] MODE_KEYS = {"mode", "type", "payment method", "channel"};

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
    };

    private final TransactionRepository transactionRepository;
    private final CategorizationService categorizationService;
    private final DuplicateDetectionService duplicateDetectionService;
    private final RecurringDetectionService recurringDetectionService;

    public StatementImportServiceImpl(TransactionRepository transactionRepository,
                                       CategorizationService categorizationService,
                                       DuplicateDetectionService duplicateDetectionService,
                                       RecurringDetectionService recurringDetectionService) {
        this.transactionRepository = transactionRepository;
        this.categorizationService = categorizationService;
        this.duplicateDetectionService = duplicateDetectionService;
        this.recurringDetectionService = recurringDetectionService;
    }

    @Override
    public StatementImportResult importStatement(User user, MultipartFile file) {
        byte[] bytes = validateAndRead(file);
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();

        List<List<String>> grid = name.endsWith(".xlsx") || name.endsWith(".xls")
                ? readWorkbook(bytes)
                : readCsv(bytes);

        StatementImportResult result = new StatementImportResult();
        List<ParsedStatementRow> rows = parse(grid, result);
        result.setTotalRows(rows.size());

        if (rows.size() > MAX_ROWS) {
            result.addMessage("Only the first " + MAX_ROWS + " rows were imported (" + rows.size() + " found).");
            rows = rows.subList(0, MAX_ROWS);
        }

        for (ParsedStatementRow row : rows) {
            ingestRow(user, row, result);
        }

        if (result.getImported() > 0) {
            try {
                recurringDetectionService.recomputeForUser(user);
            } catch (RuntimeException ex) {
                log.warn("Recurring recompute after import failed for user {}: {}", user.getId(), ex.toString());
            }
        }
        log.info("Statement import for user {}: {}", user.getId(), result.summary());
        return result;
    }

    // ---- ingest one row --------------------------------------------------

    private void ingestRow(User user, ParsedStatementRow row, StatementImportResult result) {
        if (row.getAmount() <= 0) {
            result.incrementCreditsSkipped();
            return;
        }

        Transaction txn = new Transaction();
        txn.setUser(user);
        txn.setAmount(round2(row.getAmount()));
        txn.setTransactionDate(row.getDate().atStartOfDay());
        txn.setMerchant(sanitize(deriveMerchant(row)));
        txn.setDescription(sanitize(row.getDescription()));
        txn.setPaymentMethod(sanitize(row.getPaymentMethod() != null ? row.getPaymentMethod() : "Imported"));
        txn.setStatus(TransactionStatus.SUCCESS);
        txn.setReviewStatus(ReviewStatus.NEEDS_REVIEW);

        String externalId = buildExternalId(user, row, txn);
        txn.setExternalTransactionId(externalId);

        if (transactionRepository.existsByExternalTransactionId(externalId)
                || duplicateDetectionService.findSimilarTransaction(user, txn).isPresent()) {
            result.incrementDuplicatesSkipped();
            return;
        }

        CategorySuggestion suggestion = categorizationService.suggest(user, txn.getMerchant(), txn.getDescription());
        if (suggestion.isMatched()) {
            txn.setSuggestedCategory(suggestion.getCategory());
            txn.setSuggestionReason(suggestion.getReason());
        }

        try {
            transactionRepository.save(txn);
            result.incrementImported();
        } catch (DataIntegrityViolationException ex) {
            // Lost a race on the unique constraint - treat as a duplicate.
            result.incrementDuplicatesSkipped();
        }
    }

    private String deriveMerchant(ParsedStatementRow row) {
        if (row.getMerchant() != null && !row.getMerchant().isBlank()) {
            return row.getMerchant();
        }
        // No dedicated merchant column: fall back to the first chunk of the
        // narration, which usually carries the payee.
        String desc = row.getDescription();
        if (desc == null || desc.isBlank()) {
            return "Unknown";
        }
        String[] parts = desc.split("[/|:\\-]");
        for (String p : parts) {
            String t = p.trim();
            if (t.length() >= 3 && !t.matches("(?i)upi|neft|imps|pos|ach|mandate")) {
                return t;
            }
        }
        return desc.length() > 40 ? desc.substring(0, 40) : desc;
    }

    private String buildExternalId(User user, ParsedStatementRow row, Transaction txn) {
        if (row.getReference() != null && !row.getReference().isBlank()) {
            String ref = row.getReference().replaceAll("\\s+", "");
            return "IMP-" + (ref.length() > 200 ? ref.substring(0, 200) : ref);
        }
        String basis = user.getId() + "|" + row.getDate() + "|" + txn.getAmount() + "|"
                + (txn.getMerchant() == null ? "" : txn.getMerchant().toLowerCase());
        return "IMP-H-" + sha256Hex(basis).substring(0, 32);
    }

    // ---- file validation + reading ------------------------------------------

    private byte[] validateAndRead(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a CSV or Excel statement file to import.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That file is larger than the 2 MB limit.");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!(name.endsWith(".csv") || name.endsWith(".txt") || name.endsWith(".xlsx") || name.endsWith(".xls"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported file type - upload a .csv or .xlsx statement.");
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file.");
        }
    }

    List<List<String>> readCsv(byte[] bytes) {
        List<List<String>> grid = new ArrayList<>();
        String content = new String(bytes, StandardCharsets.UTF_8);
        for (String rawLine : content.split("\r\n|\r|\n")) {
            if (rawLine.isBlank()) {
                continue;
            }
            grid.add(splitCsvLine(rawLine));
        }
        return grid;
    }

    /** Minimal RFC-4180-ish splitter: handles quoted fields and "" escapes. */
    static List<String> splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                out.add(field.toString().trim());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        out.add(field.toString().trim());
        return out;
    }

    private List<List<String>> readWorkbook(byte[] bytes) {
        List<List<String>> grid = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                return grid;
            }
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                int lastCol = row.getLastCellNum();
                for (int c = 0; c < lastCol; c++) {
                    Cell cell = row.getCell(c);
                    if (cell == null) {
                        cells.add("");
                    } else if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                            && DateUtil.isCellDateFormatted(cell)) {
                        cells.add(cell.getLocalDateTimeCellValue().toLocalDate().toString());
                    } else {
                        cells.add(formatter.formatCellValue(cell).trim());
                    }
                }
                if (cells.stream().anyMatch(s -> !s.isBlank())) {
                    grid.add(cells);
                }
            }
        } catch (IOException | RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not read that Excel file - is it a real .xlsx bank statement?");
        }
        return grid;
    }

    // ---- header detection + row mapping ------------------------------------

    private List<ParsedStatementRow> parse(List<List<String>> grid, StatementImportResult result) {
        if (grid.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file has no rows.");
        }
        List<String> header = grid.get(0);
        int dateIdx = indexOf(header, DATE_KEYS);
        int debitIdx = indexOf(header, DEBIT_KEYS);
        int amountIdx = debitIdx >= 0 ? debitIdx : indexOf(header, AMOUNT_KEYS);
        int creditIdx = indexOf(header, CREDIT_KEYS);
        int descIdx = indexOf(header, DESC_KEYS);
        int refIdx = indexOf(header, REF_KEYS);
        int modeIdx = indexOf(header, MODE_KEYS);

        if (dateIdx < 0 || amountIdx < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Couldn't find a date column and an amount/withdrawal column in the file's header row.");
        }

        List<ParsedStatementRow> rows = new ArrayList<>();
        for (int r = 1; r < grid.size(); r++) {
            List<String> cols = grid.get(r);
            int lineNumber = r + 1;

            String rawDate = at(cols, dateIdx);
            LocalDate date = parseDate(rawDate);
            if (date == null) {
                result.incrementInvalidSkipped();
                result.addMessage("Row " + lineNumber + ": unrecognised date \"" + rawDate + "\".");
                continue;
            }

            String rawAmount = at(cols, amountIdx);
            Double amount = parseAmount(rawAmount);
            // A blank debit cell with a filled credit cell is a deposit - drop it.
            if ((amount == null || amount == 0) && creditIdx >= 0 && !at(cols, creditIdx).isBlank()) {
                result.incrementCreditsSkipped();
                continue;
            }
            if (amount == null) {
                result.incrementInvalidSkipped();
                result.addMessage("Row " + lineNumber + ": unrecognised amount \"" + rawAmount + "\".");
                continue;
            }

            rows.add(new ParsedStatementRow(
                    lineNumber, date, amount,
                    null,
                    descIdx >= 0 ? at(cols, descIdx) : null,
                    modeIdx >= 0 ? at(cols, modeIdx) : null,
                    refIdx >= 0 ? at(cols, refIdx) : null));
        }
        return rows;
    }

    private static int indexOf(List<String> header, String[] keys) {
        for (String key : keys) {
            for (int i = 0; i < header.size(); i++) {
                if (header.get(i).toLowerCase(Locale.ENGLISH).contains(key)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String at(List<String> cols, int idx) {
        return idx >= 0 && idx < cols.size() && cols.get(idx) != null ? cols.get(idx).trim() : "";
    }

    static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, fmt);
            } catch (Exception ignored) {
                // try next
            }
        }
        return null;
    }

    static Double parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim()
                .replaceAll("(?i)^rs\\.?\\s*", "")   // "Rs." / "Rs" prefix
                .replace("₹", "")
                .replace(",", "")
                .replace(" ", "")
                .replace("(", "-").replace(")", "")  // (1,234.00) => -1234.00
                .replaceAll("(?i)(dr|cr)$", "");     // trailing Dr/Cr marker
        if (cleaned.isBlank() || cleaned.equals("-")) {
            return null;
        }
        try {
            return Math.abs(Double.parseDouble(cleaned));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // ---- misc helpers ---------------------------------------------------

    /**
     * Trim, cap length, and neutralise a leading spreadsheet-formula character
     * so a value like {@code =HYPERLINK(...)} can never be re-interpreted as a
     * formula if the data is later exported to CSV/Excel.
     */
    static String sanitize(String text) {
        if (text == null) {
            return null;
        }
        String t = text.replace("\r", " ").replace("\n", " ").trim();
        if (t.isEmpty()) {
            return null;
        }
        if ("=+-@".indexOf(t.charAt(0)) >= 0) {
            t = "'" + t;
        }
        return t.length() > MAX_FIELD_LEN ? t.substring(0, MAX_FIELD_LEN) : t;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(new BigInteger(1, digest).toString(16));
            while (sb.length() < 64) {
                sb.insert(0, '0');
            }
            return sb.toString();
        } catch (Exception ex) {
            // SHA-256 is always available; fall back to a stable-ish hash.
            return Integer.toHexString(input.hashCode());
        }
    }
}
