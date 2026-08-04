package com.expensetracker.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.expensetracker.entity.Category;
import com.expensetracker.entity.User;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.CategoryRepository;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public Category addCategory(Category category, User user) {
        if (categoryRepository.existsByNameIgnoreCaseAndUser(category.getName(), user)) {
            throw new IllegalArgumentException("You already have a category named '" + category.getName() + "'");
        }
        category.setUser(user);
        return categoryRepository.save(category);
    }

    @Override
    public List<Category> getAllCategoriesForUser(User user) {
        return categoryRepository.findByUserOrderByNameAsc(user);
    }

    @Override
    public List<Category> searchCategories(User user, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return getAllCategoriesForUser(user);
        }
        return categoryRepository.findByUserAndNameContainingIgnoreCaseOrderByNameAsc(user, keyword.trim());
    }

    @Override
    public Category getCategoryByIdForUser(Long id, User user) {
        return categoryRepository.findById(id)
                .filter(category -> category.getUser() != null && category.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    @Override
    public void updateCategory(Long id, User user, String newName) {
        Category category = getCategoryByIdForUser(id, user);
        if (categoryRepository.existsByNameIgnoreCaseAndUserAndIdNot(newName, user, id)) {
            throw new IllegalArgumentException("You already have a category named '" + newName + "'");
        }
        category.setName(newName);
        categoryRepository.save(category);
    }

    @Override
    public long getCategoryCount(User user) {
        return categoryRepository.countByUser(user);
    }

    @Override
    public void deleteCategory(Long id, User user) {
        Category category = getCategoryByIdForUser(id, user);
        categoryRepository.delete(category);
    }
}
