package com.expensetracker.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.expensetracker.dto.RecurringGroup;
import com.expensetracker.entity.ReviewStatus;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.User;
import com.expensetracker.repository.TransactionRepository;

@Service
public class RecurringDetectionServiceImpl implements RecurringDetectionService {

    private static final Logger log = LoggerFactory.getLogger(RecurringDetectionServiceImpl.class);

    // A series needs at least this many payments.
    private static final int MIN_OCCURRENCES = 2;
    // Amounts within the larger of these two of the running mean join a cluster.
    private static final double AMOUNT_ABS_TOLERANCE = 15.0;
    private static final double AMOUNT_PCT_TOLERANCE = 0.12;
    // Acceptable interval band (days) for a 3+ payment series.
    private static final int MIN_INTERVAL = 5;
    private static final int MAX_INTERVAL = 45;
    // For a 2-payment series we only have one gap, so demand a clearly
    // periodic one.
    private static final int TWO_HIT_WEEKLY_LO = 6, TWO_HIT_WEEKLY_HI = 9;
    private static final int TWO_HIT_MONTHLY_LO = 24, TWO_HIT_MONTHLY_HI = 38;
    // Gap regularity for 3+ payment series: stdev / mean must be under this.
    private static final double MAX_GAP_COV = 0.40;

    private final TransactionRepository transactionRepository;

    public RecurringDetectionServiceImpl(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional
    public int recomputeForUser(User user) {
        List<Transaction> txns = transactionRepository
                .findByUserAndReviewStatusNotOrderByTransactionDateAsc(user, ReviewStatus.IGNORED);

        List<Series> series = detectSeries(txns);

        Map<Long, String> memberKey = new LinkedHashMap<>();
        for (Series s : series) {
            for (Transaction t : s.members) {
                memberKey.put(t.getId(), s.groupKey);
            }
        }

        List<Transaction> changed = new ArrayList<>();
        for (Transaction t : txns) {
            String desiredKey = memberKey.get(t.getId());
            boolean desiredRecurring = desiredKey != null;
            if (t.isRecurring() != desiredRecurring
                    || !java.util.Objects.equals(t.getRecurringGroupKey(), desiredKey)) {
                t.setRecurring(desiredRecurring);
                t.setRecurringGroupKey(desiredKey);
                changed.add(t);
            }
        }
        if (!changed.isEmpty()) {
            transactionRepository.saveAll(changed);
        }
        log.debug("Recurring recompute for user {}: {} series, {} transactions updated",
                user.getId(), series.size(), changed.size());
        return series.size();
    }

    @Override
    public List<RecurringGroup> summarizeForUser(User user) {
        List<Transaction> txns = transactionRepository
                .findByUserAndReviewStatusNotOrderByTransactionDateAsc(user, ReviewStatus.IGNORED);

        List<RecurringGroup> groups = new ArrayList<>();
        for (Series s : detectSeries(txns)) {
            double monthlyEstimate = s.intervalDays > 0 ? s.typicalAmount * (30.0 / s.intervalDays) : s.typicalAmount;
            groups.add(new RecurringGroup(
                    s.groupKey, s.merchant, s.typicalAmount, s.members.size(), s.intervalDays,
                    cadenceLabel(s.intervalDays), s.lastDate, s.lastDate.plusDays(s.intervalDays), monthlyEstimate));
        }
        groups.sort(Comparator.comparingDouble(RecurringGroup::getMonthlyEstimate).reversed());
        return groups;
    }

    // ---------------------------------------------------------------------
    // Pure detection core - no Spring, no DB - so it can be unit-tested with
    // hand-built Transaction lists.
    // ---------------------------------------------------------------------

    static class Series {
        String groupKey;
        String merchant;
        double typicalAmount;
        int intervalDays;
        LocalDate lastDate;
        final List<Transaction> members = new ArrayList<>();
    }

    static List<Series> detectSeries(List<Transaction> txns) {
        // Bucket by normalized merchant.
        Map<String, List<Transaction>> byMerchant = new LinkedHashMap<>();
        for (Transaction t : txns) {
            if (t == null || t.getAmount() == null || t.getTransactionDate() == null) {
                continue;
            }
            String merchant = normalizeMerchant(t.getMerchant());
            if (merchant.isEmpty()) {
                continue;
            }
            byMerchant.computeIfAbsent(merchant, k -> new ArrayList<>()).add(t);
        }

        List<Series> result = new ArrayList<>();
        for (Map.Entry<String, List<Transaction>> entry : byMerchant.entrySet()) {
            List<Transaction> group = entry.getValue();
            if (group.size() < MIN_OCCURRENCES) {
                continue;
            }
            group.sort(Comparator.comparing(Transaction::getTransactionDate));

            for (List<Transaction> cluster : clusterByAmount(group)) {
                if (cluster.size() < MIN_OCCURRENCES) {
                    continue;
                }
                List<Long> gaps = gapsInDays(cluster);
                if (!isRegular(gaps, cluster.size())) {
                    continue;
                }
                Series s = new Series();
                s.merchant = firstNonBlankMerchant(cluster, entry.getKey());
                s.typicalAmount = median(cluster.stream().mapToDouble(Transaction::getAmount).sorted().toArray());
                s.intervalDays = (int) Math.round(median(gaps.stream().mapToDouble(Long::doubleValue).sorted().toArray()));
                s.lastDate = cluster.get(cluster.size() - 1).getTransactionDate().toLocalDate();
                s.groupKey = entry.getKey() + ":" + Math.round(s.typicalAmount);
                s.members.addAll(cluster);
                result.add(s);
            }
        }
        return result;
    }

    private static List<List<Transaction>> clusterByAmount(List<Transaction> group) {
        // Greedy: keep each cluster's running mean, attach an amount that is
        // within tolerance of it, else start a new cluster.
        List<List<Transaction>> clusters = new ArrayList<>();
        List<Double> means = new ArrayList<>();
        for (Transaction t : group) {
            double amount = t.getAmount();
            int match = -1;
            for (int i = 0; i < means.size(); i++) {
                double tol = Math.max(AMOUNT_ABS_TOLERANCE, means.get(i) * AMOUNT_PCT_TOLERANCE);
                if (Math.abs(amount - means.get(i)) <= tol) {
                    match = i;
                    break;
                }
            }
            if (match == -1) {
                List<Transaction> c = new ArrayList<>();
                c.add(t);
                clusters.add(c);
                means.add(amount);
            } else {
                List<Transaction> c = clusters.get(match);
                c.add(t);
                means.set(match, (means.get(match) * (c.size() - 1) + amount) / c.size());
            }
        }
        return clusters;
    }

    private static List<Long> gapsInDays(List<Transaction> sortedCluster) {
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < sortedCluster.size(); i++) {
            long days = ChronoUnit.DAYS.between(
                    sortedCluster.get(i - 1).getTransactionDate().toLocalDate(),
                    sortedCluster.get(i).getTransactionDate().toLocalDate());
            gaps.add(Math.abs(days));
        }
        return gaps;
    }

    static boolean isRegular(List<Long> gaps, int occurrences) {
        if (gaps.isEmpty()) {
            return false;
        }
        double median = median(gaps.stream().mapToDouble(Long::doubleValue).sorted().toArray());
        if (occurrences == 2) {
            long g = gaps.get(0);
            return (g >= TWO_HIT_WEEKLY_LO && g <= TWO_HIT_WEEKLY_HI)
                    || (g >= TWO_HIT_MONTHLY_LO && g <= TWO_HIT_MONTHLY_HI);
        }
        if (median < MIN_INTERVAL || median > MAX_INTERVAL) {
            return false;
        }
        double mean = gaps.stream().mapToLong(Long::longValue).average().orElse(0);
        if (mean <= 0) {
            return false;
        }
        double variance = gaps.stream().mapToDouble(g -> (g - mean) * (g - mean)).average().orElse(0);
        double cov = Math.sqrt(variance) / mean;
        return cov <= MAX_GAP_COV;
    }

    static String cadenceLabel(int intervalDays) {
        if (intervalDays >= 6 && intervalDays <= 9) {
            return "weekly";
        }
        if (intervalDays >= 12 && intervalDays <= 16) {
            return "fortnightly";
        }
        if (intervalDays >= 24 && intervalDays <= 38) {
            return "monthly";
        }
        return "every " + intervalDays + " days";
    }

    static String normalizeMerchant(String merchant) {
        return merchant == null ? "" : merchant.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static String firstNonBlankMerchant(List<Transaction> cluster, String fallback) {
        return cluster.stream()
                .map(Transaction::getMerchant)
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse(fallback);
    }

    static double median(double[] sortedAsc) {
        if (sortedAsc.length == 0) {
            return 0;
        }
        int mid = sortedAsc.length / 2;
        return sortedAsc.length % 2 == 1
                ? sortedAsc[mid]
                : (sortedAsc[mid - 1] + sortedAsc[mid]) / 2.0;
    }
}
