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

import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;
import com.expensetracker.service.ExpenseService;
import com.expensetracker.service.ExportService;

@Controller
public class ExportController {

    private final ExpenseService expenseService;
    private final ExportService exportService;

    public ExportController(ExpenseService expenseService, ExportService exportService) {
        this.expenseService = expenseService;
        this.exportService = exportService;
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Long categoryId,
                                             @RequestParam(required = false) String paymentMethod,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                             HttpSession session) {

        User user = CurrentUser.from(session);
        List<Expense> expenses = expenseService.searchExpenses(user, keyword, categoryId, paymentMethod, startDate, endDate);
        byte[] pdf = exportService.exportExpensesToPdf(expenses, user);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=expense-report.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Long categoryId,
                                               @RequestParam(required = false) String paymentMethod,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                               HttpSession session) {

        User user = CurrentUser.from(session);
        List<Expense> expenses = expenseService.searchExpenses(user, keyword, categoryId, paymentMethod, startDate, endDate);
        byte[] excel = exportService.exportExpensesToExcel(expenses, user);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=expense-report.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }
}
