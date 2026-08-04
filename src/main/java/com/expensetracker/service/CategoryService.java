package com.expensetracker.service;

import java.util.List;
import com.expensetracker.entity.Category;
import com.expensetracker.entity.User;

public interface CategoryService {

    Category addCategory(Category category, User user);

    List<Category> getAllCategoriesForUser(User user);

    List<Category> searchCategories(User user, String keyword);

    Category getCategoryByIdForUser(Long id, User user);

    void updateCategory(Long id, User user, String newName);

    void deleteCategory(Long id, User user);

    long getCategoryCount(User user);

}
