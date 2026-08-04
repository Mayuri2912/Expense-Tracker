package com.expensetracker.web;

import com.expensetracker.entity.User;
import jakarta.servlet.http.HttpSession;

/**
 * Centralizes how the logged-in user is read out of the HttpSession so the
 * session attribute name only has to be correct in one place.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static User from(HttpSession session) {
        return (User) session.getAttribute("loggedInUser");
    }
}
