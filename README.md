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
├── entity/        User, Category, Expense, Budget
├── repository/    Spring Data JPA repositories (user-scoped queries)
├── service/       Business logic (validation, BCrypt, search/filter, exports)
├── controller/    JSON REST API (/users, /categories, /expenses)
├── web/           Thymeleaf page controllers (login, dashboard, expenses, ...)
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
- Create the `expense_tracker` database and all 4 tables (`users`, `categories`,
  `expenses`, `budgets`) with the exact columns, foreign keys, and constraints
  the application maps to (including the `ON DELETE CASCADE` on `expenses` -
  deleting a category or user also deletes its expenses; the UI warns about
  this before a category delete is confirmed).
- Load sample data: 4 users, 16 categories, 20 expenses, 1 budget.
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
