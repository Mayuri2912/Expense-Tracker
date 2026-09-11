package com.expensetracker.dto;

import java.time.LocalDate;

/**
 * A detected series of repeating payments to the same merchant for roughly the
 * same amount at a roughly regular interval (a subscription, rent, an EMI...).
 * Read-only summary used by the review inbox badge and the Smart Insights
 * panel.
 */
public class RecurringGroup {

    private final String groupKey;
    private final String merchant;
    private final double typicalAmount;
    private final int occurrences;
    private final int intervalDays;
    private final String cadenceLabel;
    private final LocalDate lastDate;
    private final LocalDate nextEstimatedDate;
    private final double monthlyEstimate;

    public RecurringGroup(String groupKey, String merchant, double typicalAmount, int occurrences,
                           int intervalDays, String cadenceLabel, LocalDate lastDate,
                           LocalDate nextEstimatedDate, double monthlyEstimate) {
        this.groupKey = groupKey;
        this.merchant = merchant;
        this.typicalAmount = typicalAmount;
        this.occurrences = occurrences;
        this.intervalDays = intervalDays;
        this.cadenceLabel = cadenceLabel;
        this.lastDate = lastDate;
        this.nextEstimatedDate = nextEstimatedDate;
        this.monthlyEstimate = monthlyEstimate;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public String getMerchant() {
        return merchant;
    }

    public double getTypicalAmount() {
        return typicalAmount;
    }

    public int getOccurrences() {
        return occurrences;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public String getCadenceLabel() {
        return cadenceLabel;
    }

    public LocalDate getLastDate() {
        return lastDate;
    }

    public LocalDate getNextEstimatedDate() {
        return nextEstimatedDate;
    }

    public double getMonthlyEstimate() {
        return monthlyEstimate;
    }
}
