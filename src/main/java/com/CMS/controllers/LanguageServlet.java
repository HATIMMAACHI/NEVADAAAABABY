package com.campusconf.controllers;

import java.io.IOException;
import java.util.Locale;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/language")
public class LanguageServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String LANGUAGE_COOKIE_NAME = "userLanguage";
    private static final int COOKIE_MAX_AGE = 365 * 24 * 60 * 60; // 1 year in seconds

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        String language = request.getParameter("lang");
        String returnUrl = request.getParameter("returnUrl");

        if (language == null || language.trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/");
            return;
        }

        // Validate language code
        if (!isValidLanguage(language)) {
            response.sendRedirect(request.getContextPath() + "/");
            return;
        }

        // Set language in session
        HttpSession session = request.getSession();
        session.setAttribute("language", language);

        // Create and set language cookie
        Cookie languageCookie = new Cookie(LANGUAGE_COOKIE_NAME, language);
        languageCookie.setMaxAge(COOKIE_MAX_AGE);
        languageCookie.setPath("/");
        response.addCookie(languageCookie);

        // Set locale for the current request
        Locale locale = new Locale(language);
        request.setAttribute("locale", locale);

        // Redirect back to the previous page or home
        if (returnUrl != null && !returnUrl.trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + returnUrl);
        } else {
            response.sendRedirect(request.getContextPath() + "/");
        }
    }

    private boolean isValidLanguage(String language) {
        // Add supported languages here
        String[] supportedLanguages = {"en", "fr", "es", "de", "ar"};
        for (String supported : supportedLanguages) {
            if (supported.equals(language)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        doGet(request, response);
    }
} 