package com.expensetracker.web;

import java.time.LocalDate;
import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.expensetracker.entity.Category;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;
import com.expensetracker.service.CategoryService;
import com.expensetracker.service.ExpenseService;
import com.expensetracker.service.ExportService;

@Controller
public class ExportController {

    private final ExpenseService expenseService;
    private final ExportService exportService;
    private final CategoryService categoryService;

    public ExportController(ExpenseService expenseService,
                            ExportService exportService,
                            CategoryService categoryService) {
        this.expenseService = expenseService;
        this.exportService = exportService;
        this.categoryService = categoryService;
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,
            HttpSession session) {

        User user = CurrentUser.from(session);

        String categoryName = getCategoryName(categoryId, user);

        List<Expense> expenses = expenseService.searchExpenses(
                user,
                keyword,
                categoryName,
                paymentMethod,
                startDate,
                endDate
        );

        byte[] pdf = exportService.exportExpensesToPdf(expenses, user);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=expense-report.pdf"
                )
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,
            HttpSession session) {

        User user = CurrentUser.from(session);

        String categoryName = getCategoryName(categoryId, user);

        List<Expense> expenses = expenseService.searchExpenses(
                user,
                keyword,
                categoryName,
                paymentMethod,
                startDate,
                endDate
        );

        byte[] excel = exportService.exportExpensesToExcel(expenses, user);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=expense-report.xlsx"
                )
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        )
                )
                .body(excel);
    }

    private String getCategoryName(Long categoryId, User user) {
        if (categoryId == null) {
            return null;
        }

        Category category =
                categoryService.getCategoryByIdForUser(categoryId, user);

        return category != null ? category.getName() : null;
    }
}
