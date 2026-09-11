package com.expensetracker.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.expensetracker.dto.DuplicateWarning;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.User;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.TransactionRepository;

@Service
public class DuplicateDetectionServiceImpl implements DuplicateDetectionService {

    // How far apart an expense/transaction and the incoming transaction can be
    // dated and still be treated as "the same spend" (a bank's posting date
    // often lags the purchase by a day or two).
    private static final int EXPENSE_WINDOW_DAYS = 3;
    private static final int TXN_WINDOW_DAYS = 2;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // Words that carry no signal when matching a bank narration to an expense
    // title.
    private static final Set<String> NOISE = Set.of(
            "upi", "the", "for", "and", "payment", "txn", "ref", "pvt", "ltd", "india",
            "online", "transaction", "purchase", "pos", "neft", "imps", "bank");

    private final ExpenseRepository expenseRepository;
    private final TransactionRepository transactionRepository;

    public DuplicateDetectionServiceImpl(ExpenseRepository expenseRepository,
                                          TransactionRepository transactionRepository) {
        this.expenseRepository = expenseRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public Optional<DuplicateWarning> checkAgainstExpenses(User user, Transaction txn) {
        if (txn == null || txn.getTransactionDate() == null || txn.getAmount() == null) {
            return Optional.empty();
        }

        LocalDate txnDay = txn.getTransactionDate().toLocalDate();
        List<Expense> candidates = expenseRepository.findByUserAndExpenseDateBetween(
                user, txnDay.minusDays(EXPENSE_WINDOW_DAYS), txnDay.plusDays(EXPENSE_WINDOW_DAYS));

        Set<String> txnWords = words(txn.getMerchant(), txn.getDescription());

        Expense best = null;
        int bestOverlap = -1;
        double bestAmountGap = Double.MAX_VALUE;

        for (Expense e : candidates) {
            if (e.getAmount() == null || !amountsMatch(e.getAmount(), txn.getAmount())) {
                continue;
            }
            int overlap = intersectionSize(txnWords, words(e.getTitle(), e.getDescription()));
            boolean sameDay = txnDay.equals(e.getExpenseDate());
            boolean samePayment = txn.getPaymentMethod() != null
                    && txn.getPaymentMethod().equalsIgnoreCase(e.getPaymentMethod());

            // Amount already matches; require one more signal so a coincidental
            // same-amount expense on a nearby day is not flagged.
            if (overlap == 0 && !(sameDay && samePayment)) {
                continue;
            }

            double gap = Math.abs(e.getAmount() - txn.getAmount());
            if (overlap > bestOverlap || (overlap == bestOverlap && gap < bestAmountGap)) {
                best = e;
                bestOverlap = overlap;
                bestAmountGap = gap;
            }
        }

        if (best == null) {
            return Optional.empty();
        }
        String reason = String.format(
                "Same amount (₹%.2f) as “%s” you logged on %s%s.",
                best.getAmount(), best.getTitle(), DAY.format(best.getExpenseDate()),
                bestOverlap > 0 ? " (matching wording)" : "");
        return Optional.of(new DuplicateWarning(
                best.getId(), best.getTitle(), best.getExpenseDate(), best.getAmount(), reason));
    }

    @Override
    public Optional<Transaction> findSimilarTransaction(User user, Transaction incoming) {
        if (incoming == null || incoming.getTransactionDate() == null || incoming.getAmount() == null) {
            return Optional.empty();
        }
        LocalDateTime centre = incoming.getTransactionDate();
        List<Transaction> nearby = transactionRepository.findByUserAndTransactionDateBetween(
                user, centre.minusDays(TXN_WINDOW_DAYS), centre.plusDays(TXN_WINDOW_DAYS));

        Set<String> incomingWords = words(incoming.getMerchant(), incoming.getDescription());

        for (Transaction existing : nearby) {
            if (existing.getId() != null && existing.getId().equals(incoming.getId())) {
                continue;
            }
            if (existing.getAmount() == null || !amountsMatch(existing.getAmount(), incoming.getAmount())) {
                continue;
            }
            boolean sameMerchant = incoming.getMerchant() != null && existing.getMerchant() != null
                    && incoming.getMerchant().trim().equalsIgnoreCase(existing.getMerchant().trim());
            boolean sharedWords = intersectionSize(incomingWords,
                    words(existing.getMerchant(), existing.getDescription())) > 0;
            if (sameMerchant || sharedWords) {
                return Optional.of(existing);
            }
        }
        return Optional.empty();
    }

    // ---- helpers (package-private so unit tests can exercise them directly) ----

    boolean amountsMatch(double a, double b) {
        return Math.abs(a - b) <= Math.max(1.0, Math.max(a, b) * 0.01);
    }

    static Set<String> words(String... parts) {
        Set<String> out = new HashSet<>();
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            for (String token : part.toLowerCase().split("[^a-z0-9]+")) {
                if (token.length() >= 3 && !NOISE.contains(token)) {
                    out.add(token);
                }
            }
        }
        return out;
    }

    static int intersectionSize(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> copy = new HashSet<>(a);
        copy.retainAll(b);
        return copy.size();
    }
}
