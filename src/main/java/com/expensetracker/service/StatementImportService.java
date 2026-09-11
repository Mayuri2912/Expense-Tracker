package com.expensetracker.service;

import org.springframework.web.multipart.MultipartFile;

import com.expensetracker.dto.StatementImportResult;
import com.expensetracker.entity.User;

/**
 * Imports a bank-statement file (CSV or Excel) as a batch of transactions that
 * land in the review inbox as NEEDS_REVIEW. Never writes to the expenses
 * table - the user still has to accept each row.
 *
 * <p>Idempotent: a row is skipped if its reference id is already stored, or if
 * an equivalent transaction (same amount, merchant, ~date) already exists - so
 * re-uploading the same statement adds nothing.
 */
public interface StatementImportService {

    StatementImportResult importStatement(User user, MultipartFile file);
}
