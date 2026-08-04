package com.expensetracker.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.expensetracker.entity.Category;
import com.expensetracker.entity.User;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByUserOrderByNameAsc(User user);

    List<Category> findByUserAndNameContainingIgnoreCaseOrderByNameAsc(User user, String name);

    long countByUser(User user);

    boolean existsByNameIgnoreCaseAndUser(String name, User user);

    boolean existsByNameIgnoreCaseAndUserAndIdNot(String name, User user, Long id);

}
