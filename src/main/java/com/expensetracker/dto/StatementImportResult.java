package com.expensetracker.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of importing one bank-statement file: how many rows were read and
 * what happened to each. Shown as a flash message on the /transactions page.
 */
public class StatementImportResult {

    private int totalRows;
    private int imported;
    private int duplicatesSkipped;
    private int creditsSkipped;
    private int invalidSkipped;
    private final List<String> messages = new ArrayList<>();

    public void addMessage(String message) {
        if (messages.size() < 15) {
            messages.add(message);
        }
    }

    /** One-line human summary for the flash banner. */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Imported ").append(imported)
          .append(imported == 1 ? " transaction" : " transactions")
          .append(" for review");
        if (duplicatesSkipped > 0) {
            sb.append("; skipped ").append(duplicatesSkipped).append(" duplicate")
              .append(duplicatesSkipped == 1 ? "" : "s");
        }
        if (creditsSkipped > 0) {
            sb.append("; ignored ").append(creditsSkipped).append(" credit/deposit row")
              .append(creditsSkipped == 1 ? "" : "s");
        }
        if (invalidSkipped > 0) {
            sb.append("; ").append(invalidSkipped).append(" row")
              .append(invalidSkipped == 1 ? " was" : "s were").append(" unreadable");
        }
        sb.append('.');
        return sb.toString();
    }

    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }

    public int getImported() { return imported; }
    public void incrementImported() { this.imported++; }

    public int getDuplicatesSkipped() { return duplicatesSkipped; }
    public void incrementDuplicatesSkipped() { this.duplicatesSkipped++; }

    public int getCreditsSkipped() { return creditsSkipped; }
    public void incrementCreditsSkipped() { this.creditsSkipped++; }

    public int getInvalidSkipped() { return invalidSkipped; }
    public void incrementInvalidSkipped() { this.invalidSkipped++; }

    public List<String> getMessages() { return messages; }
}
