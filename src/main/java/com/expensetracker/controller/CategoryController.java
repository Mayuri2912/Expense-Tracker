package com.expensetracker.controller;

import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.expensetracker.entity.Category;
import com.expensetracker.entity.User;
import com.expensetracker.service.CategoryService;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<List<Category>> getAllCategories(HttpSession session) {
        User user = requireUser(session);
        return ResponseEntity.ok(categoryService.getAllCategoriesForUser(user));
    }

    @PostMapping
    public ResponseEntity<Category> addCategory(@RequestBody Category category, HttpSession session) {
        User user = requireUser(session);
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.addCategory(category, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id, HttpSession session) {
        User user = requireUser(session);
        categoryService.deleteCategory(id, user);
        return ResponseEntity.noContent().build();
    }

    private User requireUser(HttpSession session) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        return user;
    }
}
