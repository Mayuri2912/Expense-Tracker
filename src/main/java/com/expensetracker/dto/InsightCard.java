package com.expensetracker.dto;

/**
 * One line of "Smart Insights" shown on the dashboard. Every card is computed
 * from the user's real expenses / transactions / budget - never randomised or
 * generated.
 */
public class InsightCard {

    public enum Tone { INFO, SUCCESS, WARNING }

    private final Tone tone;
    private final String icon;   // Bootstrap Icons class, e.g. "bi-graph-up-arrow"
    private final String title;
    private final String detail;

    public InsightCard(Tone tone, String icon, String title, String detail) {
        this.tone = tone;
        this.icon = icon;
        this.title = title;
        this.detail = detail;
    }

    public static InsightCard info(String icon, String title, String detail) {
        return new InsightCard(Tone.INFO, icon, title, detail);
    }

    public static InsightCard success(String icon, String title, String detail) {
        return new InsightCard(Tone.SUCCESS, icon, title, detail);
    }

    public static InsightCard warning(String icon, String title, String detail) {
        return new InsightCard(Tone.WARNING, icon, title, detail);
    }

    public Tone getTone() {
        return tone;
    }

    /** Lower-cased tone, handy for building CSS class names in the template. */
    public String getToneClass() {
        return tone.name().toLowerCase();
    }

    public String getIcon() {
        return icon;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }
}
