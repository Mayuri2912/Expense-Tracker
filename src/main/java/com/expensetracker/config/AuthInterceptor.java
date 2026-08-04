package com.expensetracker.config;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Central authentication guard for every page in the application.
 *
 * - Adds no-cache headers to every response so the browser Back button cannot
 *   reveal a protected page after logout.
 * - Redirects any request for a protected page to /login when there is no
 *   active session, instead of relying on repeated checks in every controller.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private static final List<String> PUBLIC_PATHS = List.of("/", "/login", "/register", "/forgot-password", "/reset-password");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        // Disable caching on every response so back/forward navigation after
        // logout cannot render a page from the browser's cache.
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        String path = request.getRequestURI().substring(request.getContextPath().length());

        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }

        HttpSession session = request.getSession(false);

        if (session != null && session.getAttribute("loggedInUser") != null) {
            return true;
        }

        try {
            response.sendRedirect(request.getContextPath() + "/login");
        } catch (IOException ex) {
            log.warn("Failed to redirect unauthenticated request for {} to /login", path, ex);
        }
        return false;
    }
}
