package com.expensetracker.service;

import java.util.List;

import com.expensetracker.dto.InsightCard;
import com.expensetracker.entity.User;

/**
 * Turns the user's real data into a short list of plain-English observations
 * for the dashboard: budget burn-rate, month-over-month category movement, top
 * category, recurring spend, and the review backlog. Every number traces back
 * to an actual query.
 */
public interface InsightsService {

    List<InsightCard> forDashboard(User user);
}
