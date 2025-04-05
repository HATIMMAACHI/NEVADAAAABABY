package com.campusconf.controllers;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import com.campusconf.services.UserService;
import com.campusconf.services.PasswordResetService;
import com.campusconf.utils.DatabaseUtil;
import com.campusconf.utils.EmailUtil;
import java.sql.Connection;
import java.sql.SQLException;

@WebServlet("/password-reset/*")
public class PasswordResetServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private UserService userService;
    private PasswordResetService passwordResetService;

    @Override
    public void init() throws ServletException {
        try {
            Connection conn = DatabaseUtil.getConnection();
            userService = new UserService(conn);
            passwordResetService = new PasswordResetService(conn);
        } catch (SQLException e) {
            throw new ServletException("Failed to initialize database connection", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        
        if (pathInfo == null || pathInfo.equals("/")) {
            // Step 1: Email input
            request.getRequestDispatcher("/WEB-INF/views/password-reset/step1.jsp").forward(request, response);
        } else if (pathInfo.equals("/verify")) {
            // Step 2: Code verification
            request.getRequestDispatcher("/WEB-INF/views/password-reset/step2.jsp").forward(request, response);
        } else if (pathInfo.equals("/new-password")) {
            // Step 3: New password
            request.getRequestDispatcher("/WEB-INF/views/password-reset/step3.jsp").forward(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        HttpSession session = request.getSession();
        
        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                // Step 1: Send verification code
                String email = request.getParameter("email");
                if (!userService.isEmailExists(email)) {
                    request.setAttribute("error", "Email not found");
                    request.getRequestDispatcher("/WEB-INF/views/password-reset/step1.jsp").forward(request, response);
                    return;
                }
                
                // Generate and store verification code
                String verificationCode = passwordResetService.generateVerificationCode(email);
                
                // Send verification code via email
                String subject = "Password Reset Verification Code";
                String message = "Your verification code is: " + verificationCode;
                EmailUtil.sendEmail(email, subject, message);
                
                session.setAttribute("reset_email", email);
                response.sendRedirect(request.getContextPath() + "/password-reset/verify");
                
            } else if (pathInfo.equals("/verify")) {
                // Step 2: Verify code
                String email = (String) session.getAttribute("reset_email");
                String verificationCode = request.getParameter("verificationCode");
                
                if (!passwordResetService.verifyCode(email, verificationCode)) {
                    request.setAttribute("error", "Invalid verification code");
                    request.getRequestDispatcher("/WEB-INF/views/password-reset/step2.jsp").forward(request, response);
                    return;
                }
                
                response.sendRedirect(request.getContextPath() + "/password-reset/new-password");
                
            } else if (pathInfo.equals("/new-password")) {
                // Step 3: Set new password
                String email = (String) session.getAttribute("reset_email");
                String newPassword = request.getParameter("newPassword");
                String confirmPassword = request.getParameter("confirmPassword");
                
                if (!newPassword.equals(confirmPassword)) {
                    request.setAttribute("error", "Passwords do not match");
                    request.getRequestDispatcher("/WEB-INF/views/password-reset/step3.jsp").forward(request, response);
                    return;
                }
                
                // Get user and update password
                var user = userService.getUserByEmail(email);
                if (user != null) {
                    userService.updatePassword(user.getUserId(), newPassword);
                }
                
                // Clear session attributes
                session.removeAttribute("reset_email");
                
                // Set success message and redirect to login
                request.setAttribute("success", "Password has been reset successfully. Please login with your new password.");
                response.sendRedirect(request.getContextPath() + "/login");
            }
        } catch (Exception e) {
            System.err.println("Password reset error: " + e.getMessage());
            e.printStackTrace();
            
            request.setAttribute("error", "An error occurred during password reset. Please try again.");
            request.getRequestDispatcher("/WEB-INF/views/password-reset/step1.jsp").forward(request, response);
        }
    }

    @Override
    public void destroy() {
        // Clean up resources if needed
    }
} 