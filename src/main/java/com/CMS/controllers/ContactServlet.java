package com.campusconf.controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Properties;

import com.campusconf.utils.DatabaseUtil;
import com.campusconf.utils.EmailUtil;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/contact")
public class ContactServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String ADMIN_EMAIL = "admin@campusconf.com"; // Replace with actual admin email

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        // Display the contact form
        request.getRequestDispatcher("/WEB-INF/views/contact.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        String name = request.getParameter("name");
        String email = request.getParameter("email");
        String subject = request.getParameter("subject");
        String message = request.getParameter("message");

        // Validate input
        if (name == null || email == null || subject == null || message == null ||
            name.trim().isEmpty() || email.trim().isEmpty() || 
            subject.trim().isEmpty() || message.trim().isEmpty()) {
            request.setAttribute("error", "All fields are required");
            request.getRequestDispatcher("/WEB-INF/views/contact.jsp").forward(request, response);
            return;
        }

        // Validate email format
        if (!isValidEmail(email)) {
            request.setAttribute("error", "Invalid email format");
            request.getRequestDispatcher("/WEB-INF/views/contact.jsp").forward(request, response);
            return;
        }

        try {
            // Save contact message to database
            saveContactMessage(name, email, subject, message);

            // Send email notification to admin
            sendAdminNotification(name, email, subject, message);

            // Send confirmation email to user
            sendUserConfirmation(name, email, subject);

            // Redirect to success page
            response.sendRedirect(request.getContextPath() + "/contact?success=true");

        } catch (Exception e) {
            e.printStackTrace();
            request.setAttribute("error", "An error occurred while processing your request");
            request.getRequestDispatcher("/WEB-INF/views/contact.jsp").forward(request, response);
        }
    }

    private void saveContactMessage(String name, String email, String subject, String message) 
            throws Exception {
        String sql = 
            "INSERT INTO contact_messages (name, email, subject, message, created_at) " +
            "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, name);
            stmt.setString(2, email);
            stmt.setString(3, subject);
            stmt.setString(4, message);
            stmt.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            
            stmt.executeUpdate();
        }
    }

    private void sendAdminNotification(String name, String email, String subject, String message) 
            throws MessagingException {
        String emailSubject = "New Contact Form Submission: " + subject;
        String emailBody = String.format(
            "New contact form submission received:\n\n" +
            "Name: %s\n" +
            "Email: %s\n" +
            "Subject: %s\n\n" +
            "Message:\n%s",
            name, email, subject, message
        );

        EmailUtil.sendEmail(ADMIN_EMAIL, emailSubject, emailBody);
    }

    private void sendUserConfirmation(String name, String email, String subject) 
            throws MessagingException {
        String emailSubject = "Contact Form Submission Confirmation";
        String emailBody = String.format(
            "Dear %s,\n\n" +
            "Thank you for contacting CampusConf. We have received your message:\n\n" +
            "Subject: %s\n\n" +
            "We will review your message and get back to you as soon as possible.\n\n" +
            "Best regards,\n" +
            "CampusConf Team",
            name, subject
        );

        EmailUtil.sendEmail(email, emailSubject, emailBody);
    }

    private boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
        return email.matches(emailRegex);
    }
} 