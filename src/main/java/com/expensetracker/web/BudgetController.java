package com.expensetracker.web;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.expensetracker.entity.User;
import com.expensetracker.service.BudgetService;

@Controller
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping("/set-budget")
    public String setBudgetForm(Model model, HttpSession session) {
        User user = CurrentUser.from(session);
        LocalDate today = LocalDate.now();

        model.addAttribute("months", Month.values());
        model.addAttribute("selectedMonth", today.getMonthValue());
        model.addAttribute("selectedYear", today.getYear());

        List<Integer> years = new ArrayList<>();
        for (int y = today.getYear() - 1; y <= today.getYear() + 1; y++) {
            years.add(y);
        }
        model.addAttribute("years", years);

        budgetService.getBudget(user, today.getMonthValue(), today.getYear())
                .ifPresent(b -> model.addAttribute("existingAmount", b.getAmount()));

        return "set-budget";
    }

    @PostMapping("/save-budget")
    public String saveBudget(@RequestParam double amount,
                              @RequestParam int month,
                              @RequestParam int year,
                              HttpSession session,
                              Model model,
                              RedirectAttributes redirectAttributes) {

        if (amount <= 0) {
            model.addAttribute("error", "Budget amount must be greater than zero");
            return setBudgetForm(model, session);
        }

        budgetService.setBudget(CurrentUser.from(session), amount, month, year);
        redirectAttributes.addFlashAttribute("success", "Budget saved successfully");
        return "redirect:/dashboard";
    }
}
