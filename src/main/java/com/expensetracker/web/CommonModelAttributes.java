package com.expensetracker.web;

import jakarta.servlet.http.HttpSession;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.expensetracker.entity.User;

/**
 * Makes "loggedInUser" available in every Thymeleaf page's model automatically,
 * since the shared topbar fragment displays the logged-in user's name/avatar on
 * every authenticated page.
 */
@ControllerAdvice(basePackages = "com.expensetracker.web")
public class CommonModelAttributes {

    @ModelAttribute("loggedInUser")
    public User loggedInUser(HttpSession session) {
        return CurrentUser.from(session);
    }
}
