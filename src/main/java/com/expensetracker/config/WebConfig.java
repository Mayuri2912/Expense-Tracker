package com.expensetracker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico",
                        // REST API endpoints check the session themselves and
                        // return a 401 JSON-friendly response instead of a redirect.
                        "/users", "/users/**",
                        "/categories", "/categories/**",
                        "/expenses", "/expenses/**",
                        // The webhook has no session at all (a real payment
                        // provider calling in server-to-server), and /test
                        // does its own 401 JSON response like the other REST
                        // controllers above - neither should be redirected
                        // to the HTML /login page.
                        "/api/transactions/**"
                );
    }
}
