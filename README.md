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
- **Online Transactions** - a test/sandbox webhook simulates an online/UPI
  payment provider notifying the app of a transaction; it's checked against
  that month's budget (existing expenses + successful transactions) and
  shown on the dashboard and a dedicated `/transactions` page. See
  [Online Transaction Monitoring](#online-transaction-monitoring) below.
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
├── entity/        User, Category, Expense, Budget, Transaction, TransactionStatus
├── repository/    Spring Data JPA repositories (user-scoped queries)
├── service/       Business logic (validation, BCrypt, search/filter, exports, transactions)
├── dto/           Request/response shapes for the transaction webhook/test API
├── controller/    JSON REST API (/users, /categories, /expenses, /api/transactions)
├── web/           Thymeleaf page controllers (login, dashboard, expenses, transactions, ...)
├── config/        BCrypt bean, auth interceptor, MVC config
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
  `expenses`, `budgets`, `password_reset_otps`, `transactions`) with the exact
  columns, foreign keys, and constraints the application maps to (including
  the `ON DELETE CASCADE` on `expenses` - deleting a category or user also
  deletes its expenses; the UI warns about this before a category delete is
  confirmed).
- Load sample data: 4 users, 16 categories, 20 expenses, 1 budget, 2 sample
  online transactions.
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

## Online Transaction Monitoring

If a user makes an online/UPI-style payment but forgets to log it as an Expense,
the app can receive that transaction and check it against their monthly budget.

**This does not read real Google Pay/PhonePe/Paytm transaction history** - no
such consumer API exists for a third party to poll. Instead, there's a
test/sandbox webhook that stands in for what a real payment provider's
server-to-server callback would look like. Swapping in a real provider later
means pointing it at the same webhook URL (or adding a translation layer in
front of it) - `TransactionService` and everything downstream doesn't need to
change.

**Design note:** a `Transaction` is never auto-converted into an `Expense`.
`Expense.category` is required (`NOT NULL`) and an incoming transaction never
carries a category, so auto-creating one would mean inventing a fake category
behind the user's back. Instead, budget checks add that month's successful
transactions on top of existing expenses, so nothing is silently duplicated
or miscategorized. (`Transaction` does carry a nullable `expense` reference,
reserved for a future "convert this into an Expense" action.)

### Endpoints

- `POST /api/transactions/webhook` - simulates a payment provider; identifies
  the user by `userEmail` in the body (a real webhook has no session).
- `POST /api/transactions/test` - requires login; always uses the logged-in
  session user, so it can't be used to submit a transaction as someone else.
  Can be disabled via `app.transactions.test-endpoint-enabled=false` (or the
  `TRANSACTION_TEST_ENDPOINT_ENABLED` environment variable).
- `/transactions` page - the same test endpoint exposed as a simple form, plus
  the logged-in user's transaction history.

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

### Expected budget-exceeded behavior

Budget ₹10,000, existing expenses ₹9,500, incoming transaction ₹1,000 →
combined spending ₹10,500, so the response (and the dashboard) reports:

```json
{
  "budgetStatus": "danger",
  "budgetMessage": "Your monthly budget has been exceeded by ₹500.00.",
  "monthlySpending": 10500.0,
  "budgetAmount": 10000.0,
  "remainingBudget": -500.0
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
- [x] Online transaction webhook + test endpoint, budget-crossing detection,
      duplicate protection, per-user isolation, dashboard + `/transactions` page

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

## Future Scope

- Recurring/subscription expenses with automatic monthly entries
- Multi-currency support
- Shared/household budgets across multiple users
- Email notifications when a budget threshold is crossed
- Pagination for very large expense lists
- A real payment-provider integration behind the transaction webhook
- "Convert to Expense" action to reconcile a Transaction into a categorized Expense
