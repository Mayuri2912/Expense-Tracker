package com.expensetracker.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.expensetracker.dto.InsightCard;
import com.expensetracker.dto.RecurringGroup;
import com.expensetracker.entity.Budget;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.ReviewStatus;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.TransactionStatus;
import com.expensetracker.entity.User;
import com.expensetracker.repository.TransactionRepository;

@Service
public class InsightsServiceImpl implements InsightsService {

    private static final int MAX_CARDS = 6;

    private final ExpenseService expenseService;
    private final BudgetService budgetService;
    private final TransactionRepository transactionRepository;
    private final RecurringDetectionService recurringDetectionService;
    private final DuplicateDetectionService duplicateDetectionService;

    public InsightsServiceImpl(ExpenseService expenseService,
                               BudgetService budgetService,
                               TransactionRepository transactionRepository,
                               RecurringDetectionService recurringDetectionService,
                               DuplicateDetectionService duplicateDetectionService) {
        this.expenseService = expenseService;
        this.budgetService = budgetService;
        this.transactionRepository = transactionRepository;
        this.recurringDetectionService = recurringDetectionService;
        this.duplicateDetectionService = duplicateDetectionService;
    }

    @Override
    public List<InsightCard> forDashboard(User user) {
        List<InsightCard> cards = new ArrayList<>();
        LocalDate today = LocalDate.now();
        int month = today.getMonthValue();
        int year = today.getYear();
        String monthName = today.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        List<Expense> allExpenses = safeList(expenseService.getAllExpensesForUser(user));
        Map<String, Double> thisMonth = categoryTotals(allExpenses, month, year);
        YearMonth prev = YearMonth.of(year, month).minusMonths(1);
        Map<String, Double> lastMonth = categoryTotals(allExpenses, prev.getMonthValue(), prev.getYear());
        double thisMonthTotal = thisMonth.values().stream().mapToDouble(Double::doubleValue).sum();

        addBurnRateCard(cards, user, month, year, monthName, today);
        addReviewBacklogCard(cards, user, month, year);
        addDuplicateCard(cards, user);
        addMonthOverMonthCard(cards, thisMonth, lastMonth);
        addTopCategoryCard(cards, thisMonth, thisMonthTotal, monthName);
        addRecurringCard(cards, user);

        return cards.size() > MAX_CARDS ? cards.subList(0, MAX_CARDS) : cards;
    }

    // ---- individual insights ------------------------------------------------

    private void addBurnRateCard(List<InsightCard> cards, User user, int month, int year,
                                  String monthName, LocalDate today) {
        Budget budget = budgetService.getBudget(user, month, year).orElse(null);
        if (budget == null || budget.getAmount() == null || budget.getAmount() <= 0) {
            return;
        }
        double spentSoFar = expenseService.getMonthlyExpenseAmount(user, month, year);
        if (spentSoFar <= 0) {
            return;
        }
        int dayOfMonth = today.getDayOfMonth();
        int daysInMonth = today.lengthOfMonth();
        double projected = spentSoFar / dayOfMonth * daysInMonth;
        double budgetAmount = budget.getAmount();

        if (projected > budgetAmount * 1.02) {
            cards.add(InsightCard.warning("bi-graph-up-arrow", "Budget forecast",
                    String.format("At your current pace you're on track to spend about %s by the end of %s — roughly %s over your %s budget.",
                            money(projected), monthName, money(projected - budgetAmount), money(budgetAmount))));
        } else {
            cards.add(InsightCard.success("bi-graph-up-arrow", "Budget forecast",
                    String.format("On track: at your current pace you'll spend about %s this month, within your %s budget.",
                            money(projected), money(budgetAmount))));
        }
    }

    private void addReviewBacklogCard(List<InsightCard> cards, User user, int month, int year) {
        long pending = transactionRepository.countByUserAndReviewStatus(user, ReviewStatus.NEEDS_REVIEW);
        if (pending == 0) {
            return;
        }
        double pendingThisMonth = transactionRepository.sumAmountByUserAndStatusAndReviewStatusAndMonthAndYear(
                user, TransactionStatus.SUCCESS, ReviewStatus.NEEDS_REVIEW, month, year);
        cards.add(InsightCard.warning("bi-inbox",
                pending + (pending == 1 ? " transaction awaiting review" : " transactions awaiting review"),
                pendingThisMonth > 0
                        ? String.format("About %s of this month's spending is in imported transactions you haven't reviewed yet, so it isn't in your budget figure.", money(pendingThisMonth))
                        : "Review them to fold them into your expenses and budget."));
    }

    private void addDuplicateCard(List<InsightCard> cards, User user) {
        List<Transaction> pending = transactionRepository
                .findByUserAndReviewStatusOrderByTransactionDateDesc(user, ReviewStatus.NEEDS_REVIEW);
        int dupes = 0;
        for (Transaction t : pending) {
            if (duplicateDetectionService.checkAgainstExpenses(user, t).isPresent()) {
                dupes++;
            }
        }
        if (dupes > 0) {
            cards.add(InsightCard.warning("bi-files",
                    dupes + (dupes == 1 ? " possible duplicate" : " possible duplicates"),
                    (dupes == 1 ? "One pending transaction looks" : dupes + " pending transactions look")
                            + " like expenses you already entered by hand — check before accepting."));
        }
    }

    private void addMonthOverMonthCard(List<InsightCard> cards, Map<String, Double> thisMonth,
                                        Map<String, Double> lastMonth) {
        String movedCategory = null;
        double movedPct = 0, curr = 0, prevValue = 0;
        for (Map.Entry<String, Double> e : thisMonth.entrySet()) {
            double now = e.getValue();
            double before = lastMonth.getOrDefault(e.getKey(), 0.0);
            if (before <= 0) {
                continue;
            }
            double delta = now - before;
            double pct = delta / before * 100.0;
            if (Math.abs(delta) >= 200 && Math.abs(pct) >= 15 && Math.abs(pct) > Math.abs(movedPct)) {
                movedCategory = e.getKey();
                movedPct = pct;
                curr = now;
                prevValue = before;
            }
        }
        if (movedCategory == null) {
            return;
        }
        if (movedPct > 0) {
            cards.add(InsightCard.warning("bi-arrow-up-right", movedCategory + " spending is up",
                    String.format("%s is up %.0f%% versus last month (%s vs %s).",
                            movedCategory, movedPct, money(curr), money(prevValue))));
        } else {
            cards.add(InsightCard.success("bi-arrow-down-right", movedCategory + " spending is down",
                    String.format("%s is down %.0f%% versus last month (%s vs %s).",
                            movedCategory, Math.abs(movedPct), money(curr), money(prevValue))));
        }
    }

    private void addTopCategoryCard(List<InsightCard> cards, Map<String, Double> thisMonth,
                                     double thisMonthTotal, String monthName) {
        if (thisMonthTotal <= 0) {
            return;
        }
        String topCat = null;
        double topAmount = 0;
        for (Map.Entry<String, Double> e : thisMonth.entrySet()) {
            if (e.getValue() > topAmount) {
                topAmount = e.getValue();
                topCat = e.getKey();
            }
        }
        if (topCat == null) {
            return;
        }
        double share = topAmount / thisMonthTotal * 100.0;
        cards.add(InsightCard.info("bi-pie-chart", "Top category in " + monthName,
                String.format("%s is your biggest category this month: %s (%.0f%% of spending so far).",
                        topCat, money(topAmount), share)));
    }

    private void addRecurringCard(List<InsightCard> cards, User user) {
        List<RecurringGroup> groups = recurringDetectionService.summarizeForUser(user);
        if (groups.isEmpty()) {
            return;
        }
        double monthly = groups.stream().mapToDouble(RecurringGroup::getMonthlyEstimate).sum();
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < Math.min(3, groups.size()); i++) {
            if (i > 0) {
                names.append(", ");
            }
            names.append(groups.get(i).getMerchant());
        }
        if (groups.size() > 3) {
            names.append(" and ").append(groups.size() - 3).append(" more");
        }
        cards.add(InsightCard.info("bi-arrow-repeat",
                groups.size() + (groups.size() == 1 ? " recurring payment detected" : " recurring payments detected"),
                String.format("Roughly %s per month across: %s.", money(monthly), names)));
    }

    // ---- helpers ----------------------------------------------------------

    private Map<String, Double> categoryTotals(List<Expense> expenses, int month, int year) {
        Map<String, Double> totals = new LinkedHashMap<>();
        for (Expense e : expenses) {
            LocalDate d = e.getExpenseDate();
            if (d == null || d.getMonthValue() != month || d.getYear() != year || e.getAmount() == null) {
                continue;
            }
            String name = (e.getCategory() != null && e.getCategory().getName() != null)
                    ? e.getCategory().getName() : "Uncategorized";
            totals.merge(name, e.getAmount(), Double::sum);
        }
        return totals;
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String money(double amount) {
        return "₹" + String.format(Locale.ENGLISH, "%,.0f", amount);
    }
}
