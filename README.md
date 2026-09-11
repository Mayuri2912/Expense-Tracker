# Expense Tracker

A full-stack **Spring Boot 4.1 + Thymeleaf** web application for tracking personal expenses,
organizing them by category, setting monthly budgets, and visualizing spending trends.

Originally built as an MCA coursework project, this version has been completed with
authentication, validation, budgeting, charts, search/filtering, and PDF/Excel export
so it can be submitted for coursework or shown in an internship/placement portfolio.

## Features

- **Authentication** - session-based login/registration, BCrypt password hashing,
  every page protected by a central interceptor, browser caching disabled so the
  Back button can't reveal a page after logout.
- **Dashboard** - stat cards, monthly budget progress bar with color-coded alerts
  (80% warning, over-budget danger), a category breakdown pie chart, and a 6-month
  spending trend bar chart (Chart.js).
- **Expenses** - add/edit/delete with validation, search by title, filter by
  category/payment method/date range, delete-confirmation modal.
- **Categories** - add/edit/delete (scoped per user), search, delete-confirmation modal.
- **Budgets** - set a budget per month/year; the dashboard calculates how much of
  it has been spent and how much is remaining.
- **Profile** - view account info and stats, update name and password.
- **Settings** - dark mode toggle (persisted in the browser), change password,
  app version/about info.
- **Export** - download the currently filtered expense list as PDF or Excel.
- **Smart Transaction Review Hub** - ingest online/UPI transactions (a
  sandbox webhook, a manual test form, or a CSV/Excel bank-statement import),
  get a rule-based category suggestion with a plain-English reason, see
  duplicate and recurring-payment warnings, then Accept (creates a real,
  categorized Expense), Edit, or Ignore each one from a review inbox -
  nothing is ever added to your expenses without that explicit step. A
  Smart Insights panel on the dashboard summarizes budget burn-rate,
  month-over-month category movement, and the review backlog, all computed
  from real data. See [Smart Transaction Review Hub](#smart-transaction-review-hub) below.
- **Security** - passwords are BCrypt-hashed and never returned in API responses;
  every expense/category/budget is scoped to its owning user; a logged-in user
  cannot view or modify another user's data.

## Tech Stack

- Java 17, Spring Boot 4.1 (Spring Framework 7)
- Spring MVC (`spring-boot-starter-webmvc`), Spring Data JPA, Hibernate
- MySQL
- Thymeleaf + Bootstrap 5.3 + Bootstrap Icons
- Chart.js (dashboard charts)
- BCrypt (`spring-security-crypto`) for password hashing
- OpenPDF and Apache POI for PDF/Excel export
- Maven

## Project Structure

```
src/main/java/com/expensetracker/
├── entity/        User, Category, Expense, Budget, Transaction, TransactionStatus,
│                  ReviewStatus, CategoryRule, RuleMatchType
├── repository/    Spring Data JPA repositories (user-scoped queries)
├── service/       Business logic - expenses/categories/budgets/exports, plus the
│                  Review Hub engine: CategorizationService, DuplicateDetectionService,
│                  RecurringDetectionService, InsightsService, StatementImportService
├── dto/           Request/response + view-model shapes (transaction webhook/test API,
│                  CategorySuggestion, DuplicateWarning, RecurringGroup, InsightCard,
│                  TransactionReviewView, StatementImportResult, ...)
├── controller/    JSON REST API (/users, /categories, /expenses, /api/transactions)
├── web/           Thymeleaf page controllers (login, dashboard, expenses, transactions, ...)
├── config/        BCrypt bean, auth interceptor, MVC config, CategoryRuleSeeder
└── exception/     Centralized error handling (web pages vs. REST API)

src/main/resources/templates/
├── fragments/layout.html   shared sidebar/topbar/alerts/scripts
└── *.html                  one template per page
```

## Getting Started

### 1. Database Setup

This project ships with `database/expense_tracker.sql`, which is verified against a
real export of the production database (not just generated from the entity
classes) - it recreates the exact schema the app expects, including sample data,
and can be imported directly:

```bash
mysql -u root -p < database/expense_tracker.sql
```

This will:
- Create the `expense_tracker` database and all tables (`users`, `categories`,
  `expenses`, `budgets`, `password_reset_otps`, `transactions`,
  `category_rules`) with the exact columns, foreign keys, and constraints the
  application maps to (including the `ON DELETE CASCADE` on `expenses` -
  deleting a category or user also deletes its expenses; the UI warns about
  this before a category delete is confirmed).
- Load sample data: 4 users, 16 categories, 20 expenses, 1 budget, 3 sample
  online transactions (one already accepted, two awaiting review - including
  a pair dated a month apart so recurring detection has something to find),
  and ~50 built-in categorization rules.
- All 4 sample users share the password `123456` (see table below) - stored as
  a real BCrypt hash, not plain text, so login works immediately.

| Email | Password |
|---|---|
| mayuri@gmail.com | 123456 |
| rahul@gmail.com | 123456 |
| priya@gmail.com | 123456 |
| amit@gmail.com | 123456 |

`spring.jpa.hibernate.ddl-auto=update` in `application.properties` is kept
intentionally (rather than `validate`) so Hibernate can still add any future
column/table automatically without requiring a schema migration tool, without
risk of failing startup over minor type-mapping differences that `validate`
mode is strict about.

### 2. Configure the Database Connection

Edit `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/expense_tracker
spring.datasource.username=root
spring.datasource.password=root
```

### 3. Run the Application

```bash
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080`. Register an account, then log in.

### 4. Build a JAR

```bash
./mvnw clean package
java -jar target/expense-tracker-0.0.1-SNAPSHOT.jar
```

## Smart Transaction Review Hub

**The problem:** a plain expense tracker only records what you bother to type
in. Online/UPI spending is easy to forget, hard to categorize consistently,
easy to log twice, and easy to lose track of when it's a recurring
subscription. This feature adds an intelligence + review layer on top of the
existing Expense module - it ingests transaction data, suggests a category
with a reason, flags likely duplicates and recurring payments, and lets the
user Accept / Edit / Ignore each one before it becomes a real expense. The
Expense table stays the single, trusted source of confirmed spending.

```
 SOURCE                INGEST              ANALYZE (deterministic)         REVIEW            CONFIRMED
 sandbox webhook   ┐                  ┌ CategorizationService  ┐                        ┌ ExpenseService
 CSV/XLSX import   ├─▶ Transaction  ─▶├ DuplicateDetection     ├─▶ /transactions  ─▶ Accept ─▶ .addExpense
 manual test form  ┘   (NEEDS_REVIEW) └ RecurringDetection     ┘   review inbox         │       (unchanged)
                                                                                     Ignore
                                                                                  (never counted)
```

**This does not read real Google Pay/PhonePe/Paytm transaction history** - no
such consumer API exists for a third party to poll, and the app never asks
for or handles a real bank username/password. Ingestion is one of: a
test/sandbox webhook standing in for what a real payment provider's
server-to-server callback would look like, a CSV/Excel bank-statement upload,
or a manual "simulate a transaction" form. See
[Production Bank Integration](#production-bank-integration-boundary) below
for what swapping in a real provider would actually require.

**Design note:** a `Transaction` is never auto-converted into an `Expense`.
`Expense.category` is required (`NOT NULL`) and an incoming transaction has no
category until the categorization engine suggests one (or the user picks
one), so auto-creating an expense would mean guessing on the user's behalf.
Instead, every transaction starts `NEEDS_REVIEW` and only becomes an Expense
when the user explicitly **Accepts** it - at which point `ExpenseService
.addExpense(...)` runs exactly as it does for a hand-typed expense, so every
existing chart, budget figure, search/filter, and PDF/Excel export picks it
up with zero code changes. The dashboard's primary budget figure only ever
counts expenses (hand-typed + accepted) - a separate "N transactions awaiting
review, ₹X not yet counted" line keeps the user honestly informed without
silently inflating their budget number.

### The categorization engine (deterministic, no AI/external API)

A small `category_rules` table maps merchant/description text to a category
name: `CONTAINS`/`EQUALS` pattern → category, with a priority. Built-in
defaults (India-first: Swiggy/Zomato → Food, Amazon/Flipkart → Shopping,
Uber/Ola → Travel, "electricity"/"recharge" → Bills, "rent" → Rent, "atm" →
Cash, Netflix/Spotify → Entertainment, ...) are seeded once at startup by
`CategoryRuleSeeder` from `default-category-rules.csv` and are also editable
- from the review inbox, checking "always categorize this merchant this way"
on Accept saves a **personal** rule (checked before the defaults) so the
engine gets smarter with use. Every suggestion carries a plain-English reason
("Merchant text contains 'swiggy' → Food · high confidence") and, if the user
has no category by that name yet, says so instead of guessing.

A rule-based engine was chosen over an LLM/AI API deliberately: it is free,
works fully offline (no API key, no cost, no dependency on a third-party
service being reachable during a demo), sends no user data anywhere, and -
critically for a graded project - every decision it makes can be explained in
one sentence rather than "the model said so".

### Duplicate & recurring detection (statistical, no external data)

- **Duplicate detection** compares an incoming transaction to the user's
  existing expenses within a ±3 day window: amount within ~1% and either
  shared wording (merchant/description tokens) or same day + same payment
  method. Flagged as a warning in the review inbox - the user decides.
- **Recurring detection** clusters a user's transactions by merchant and
  amount, then checks whether the gaps between occurrences are regular
  (weekly ~6-9 days, monthly ~24-38 days, or a low coefficient of variation
  for 3+ occurrences). Detected series are flagged on their transactions and
  summarized in a "Recurring Payments Detected" card with an estimated
  monthly cost.

### Statement import

`POST /transactions/import` (multipart) accepts a `.csv`/`.txt` or
`.xlsx`/`.xls` file (2 MB / 500 rows max): a dependency-free CSV parser and
Apache POI (already a dependency, for Excel export) read it, a header-keyword
scan finds the date/amount/description/reference columns regardless of exact
bank format, credit/deposit rows are skipped automatically, and each debit row
is deduplicated - by its own reference column if present, otherwise by a
SHA-256 hash of user+date+amount+merchant - so **re-importing the same file
adds nothing twice**. Every kept row is run through the same categorization
and duplicate-detection logic as the webhook path before landing in the
review inbox.

### Endpoints

- `POST /api/transactions/webhook` - simulates a payment provider; identifies
  the user by `userEmail` in the body (a real webhook has no session).
- `POST /api/transactions/test` - requires login; always uses the logged-in
  session user. Can be disabled via `app.transactions.test-endpoint-enabled=false`
  (or the `TRANSACTION_TEST_ENDPOINT_ENABLED` environment variable).
- `POST /api/transactions/{id}/accept` / `/ignore` - REST equivalents of the
  review-inbox actions, session-gated.
- `POST /transactions/import` - statement upload (web form).
- `POST /transactions/{id}/accept` / `/ignore` / `/unignore` - the review
  inbox's Accept / Ignore / "move back to review" actions (web form).
- `/transactions` page - review inbox, recurring-payment summary, import
  form, sandbox test form, and full accepted/ignored history.

### Security & privacy

- No bank credentials are ever collected; no scraping.
- The webhook and `/test` endpoint are excluded from the redirect-to-login
  interceptor but do their own session/ownership checks (`/test`, accept,
  ignore) - same pattern as the existing `/expenses`, `/categories` REST API.
- Every query is scoped to the logged-in user (`findByIdAndUser`,
  `findByUserAnd...`); accepting or ignoring someone else's transaction ID
  returns 404, never another user's data.
- Uploaded statement text is trimmed, length-capped, and a leading
  `=`/`+`/`-`/`@` character is neutralized (CSV/formula-injection guard)
  before being stored or later exported.
- The categorization engine and duplicate/recurring detection run entirely
  in-process on data the user already owns - nothing is sent to a third party.

### Production Bank Integration boundary

This is the honest line between what's implemented and what a real
integration would need:

| | This project (sandbox) | A production integration |
|---|---|---|
| Transaction source | Test webhook / manual form / CSV-XLSX upload | A licensed Account Aggregator (AA) or a bank/PSP's real webhook, requiring institutional onboarding, signed agreements, and regulatory compliance (RBI AA framework in India) |
| Credentials | None collected | Never the app's business either - an AA/PSP integration uses OAuth-style consent, never a bank username/password |
| Identification | `userEmail` in the payload (demo-only) | A signed, verified callback tied to a consented account link |
| Swapping one for the other | Point the same provider at `/api/transactions/webhook`, or add a thin translation layer in front of it | `TransactionService` and everything downstream (categorization, dedupe, recurring detection, review inbox) needs **no changes** - the abstraction boundary is exactly `TransactionWebhookRequest` |

### Example request

```json
POST /api/transactions/webhook
{
  "transactionId": "TXN-10001",
  "userEmail": "mayuri@gmail.com",
  "amount": 1000.00,
  "paymentMethod": "UPI",
  "merchant": "Amazon",
  "transactionDate": "2026-08-21T15:30:00",
  "status": "SUCCESS"
}
```

Sending the same `transactionId` twice returns the same result with
`"duplicate": true` and does not create a second row - protected by a unique
constraint on `transactions.external_transaction_id`, a service-level
existence check, and a fallback catch for the rare case of two concurrent
deliveries racing each other.

## Implemented Features Checklist

- [x] Login / Registration / Logout with BCrypt password hashing
- [x] Session-based auth guarding every page, no-cache headers after logout
- [x] Dashboard with live stats, budget progress bar, and alerts
- [x] Category-wise pie chart and 6-month trend bar chart
- [x] Add/Edit/Delete Expense with Bean Validation and delete-confirmation modal
- [x] Add/Edit/Delete Category (per-user, unique per user) with search
- [x] Search & filter expenses by keyword, category, payment method, date range
- [x] Monthly Budget entity + Set/Update Budget page
- [x] Budget alerts (80% warning, over-budget danger) with a progress bar
- [x] Profile page with real account data + edit name/password
- [x] Settings page with working dark mode toggle, change password, app version
- [x] PDF export and Excel export of the filtered expense list
- [x] Every expense/category/budget scoped to the logged-in user
- [x] Centralized exception handling (friendly error page / JSON error body)
- [x] Constructor injection throughout services and controllers
- [x] Smart Transaction Review Hub: webhook + CSV/Excel statement import +
      test endpoint, rule-based categorization with reason/confidence,
      duplicate detection (vs. expenses and vs. other transactions),
      recurring-payment detection, Accept/Edit/Ignore review workflow that
      creates real linked Expenses, personal "remember this" rules,
      per-user isolation, Smart Insights dashboard panel
- [x] Unit tests for the categorization, duplicate-detection, recurring-
      detection, and statement-parsing logic (pure, no Spring/DB required)

## Database Sync Notes

The Java code was verified against a real `mysqldump` of the production database
(not just assumed from the entity classes) and three real mismatches were found
and fixed. No table was renamed and no primary/foreign key was changed - these
were Java-side fixes plus data-only corrections:

1. **`users.name`** - the live database has a legacy `name` column (NOT NULL)
   alongside `full_name` that the entity didn't know about. `User.java` now
   keeps a hidden `name` field automatically mirrored from `fullName` on every
   save (`@PrePersist`/`@PreUpdate`) - it's never exposed on any form.
2. **`expenses.category_id` is `NOT NULL`** - the app previously allowed adding
   an expense with no category selected, which would have failed with a SQL
   constraint violation. Category selection is now required and validated
   before the form ever reaches the database (`ExpenseWebController` rejects
   the submission with a normal validation message instead of a 500 error).
3. **`expenses` foreign keys use `ON DELETE CASCADE`** - deleting a category
   (or a user) silently deletes every expense linked to it at the database
   level. The category delete confirmation now shows exactly how many expenses
   will be removed before you confirm.

No configuration changes were required in `application.properties` beyond what
was already there - `ddl-auto=update` continues to work correctly against this
schema since it only adds missing columns/tables and never touches existing ones.

4. **A MySQL-specific `ddl-auto=update` gotcha, found while building the
   Review Hub:** adding a `NOT NULL` enum column with no explicit SQL
   `DEFAULT` to a table that already has rows makes MySQL silently backfill
   those rows with the *alphabetically first* enum value - not the entity's
   Java default. `Transaction.reviewStatus` hit exactly this (existing rows
   were backfilled to `ACCEPTED` instead of the intended `NEEDS_REVIEW`).
   Fixed by giving the column an explicit `columnDefinition` with a real SQL
   `DEFAULT` (see the comment on `Transaction.reviewStatus`), and worth
   remembering for any future `NOT NULL` column added to a table that may
   already have data.

## Future Scope

- Auto-logging the *next* occurrence of a detected recurring payment as a
  draft expense, rather than only flagging past occurrences
- Multi-currency support
- Shared/household budgets across multiple users
- Email notifications when a budget threshold is crossed, or when a new
  transaction needs review
- Pagination for very large expense/transaction lists
- A real Account Aggregator / payment-provider integration behind the
  transaction webhook - see
  [Production Bank Integration boundary](#production-bank-integration-boundary)
- A user-facing settings page for editing/reordering personal category rules
  directly (today they're created via "remember this" on Accept)
