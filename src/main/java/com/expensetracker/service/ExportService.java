package com.expensetracker.service;

import java.util.List;

import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;

public interface ExportService {

    byte[] exportExpensesToPdf(List<Expense> expenses, User user);

    byte[] exportExpensesToExcel(List<Expense> expenses, User user);

}
