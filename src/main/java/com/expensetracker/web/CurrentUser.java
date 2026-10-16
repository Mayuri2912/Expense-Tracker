package com.expensetracker.web;

import com.expensetracker.entity.User;
import jakarta.servlet.http.HttpSession;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static User from(HttpSession session) {
        return (User) session.getAttribute("loggedInUser");
    }
}