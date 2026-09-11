package com.expensetracker.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A single "if the transaction text matches PATTERN, suggest CATEGORY" rule
 * used by the categorization engine. This is what makes the auto-categorization
 * <em>configurable</em> rather than hard-coded.
 *
 * <p>Two flavours:
 * <ul>
 *   <li><b>Built-in default</b> - {@code user} is {@code null}. Seeded once at
 *       startup from {@code default-category-rules.csv} (Swiggy -&gt; Food,
 *       Amazon -&gt; Shopping, "ELECTRICITY" -&gt; Bills, ...). Applies to
 *       every user. Because categories are per-user, a default rule targets a
 *       category by <b>name</b> ({@link #categoryName}); the engine resolves
 *       that to the user's own category at match time, and simply shows the
 *       name as a hint if the user has no category by that name.</li>
 *   <li><b>Personal</b> - {@code user} is set. Created from the review inbox
 *       ("remember this: always categorize SWIGGY as Food"). It also carries a
 *       direct {@link #category} link so it keeps working if the category is
 *       later renamed. Personal rules are checked before defaults.</li>
 * </ul>
 *
 * <p>No regex (see {@link RuleMatchType}) and no unique constraint - a
 * duplicate rule is harmless because the engine takes the first match ordered
 * by {@link #priority}, then personal-before-default.
 */
@Entity
@Table(name = "category_rules",
       indexes = @Index(name = "idx_category_rules_user", columnList = "user_id"))
public class CategoryRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // null = built-in default rule that applies to every user.
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 20)
    private RuleMatchType matchType = RuleMatchType.CONTAINS;

    // Always stored lower-case (the service normalizes on save) so matching is
    // a plain case-insensitive substring/equality test.
    @NotBlank(message = "Rule pattern is required")
    @Column(nullable = false)
    private String pattern;

    // The suggestion target, by name - the only form that works for a
    // cross-user default rule. Always set.
    @NotBlank(message = "Category name is required")
    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    // Optional direct link, set for personal rules so a category rename does
    // not silently break the rule. Null for built-in defaults.
    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    // Lower value = evaluated first. Personal rules are additionally always
    // considered before defaults regardless of this number.
    @Column(nullable = false)
    private Integer priority = 100;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public CategoryRule() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.priority == null) {
            this.priority = 100;
        }
        if (this.matchType == null) {
            this.matchType = RuleMatchType.CONTAINS;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public RuleMatchType getMatchType() {
        return matchType;
    }

    public void setMatchType(RuleMatchType matchType) {
        this.matchType = matchType;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
