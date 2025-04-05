package com.campusconf.controllers;

import com.campusconf.utils.ConfigUtil;
import com.campusconf.utils.EmailUtil;
import com.campusconf.utils.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/email/*")
public class EmailServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not authenticated");
            return;
        }

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid request path");
            return;
        }

        try {
            switch (pathInfo) {
                case "/send":
                    handleSendEmail(request, response);
                    break;
                case "/send-submission-id":
                    handleSendSubmissionId(request, response);
                    break;
                case "/send-password-reset":
                    handleSendPasswordReset(request, response);
                    break;
                case "/send-decision":
                    handleSendDecision(request, response);
                    break;
                default:
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid email operation");
            }
        } catch (Exception e) {
            handleError(response, "Error processing email request", e);
        }
    }

    private void handleSendEmail(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        String to = request.getParameter("to");
        String subject = request.getParameter("subject");
        String body = request.getParameter("body");

        if (to == null || subject == null || body == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing required fields");
            return;
        }

        try {
            EmailUtil.sendEmail(to, subject, body);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(JsonUtil.toJsonObject(Map.of("status", "success")));
        } catch (Exception e) {
            handleError(response, "Failed to send email", e);
        }
    }

    private void handleSendSubmissionId(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        String to = request.getParameter("to");
        String submissionId = request.getParameter("submissionId");

        if (to == null || submissionId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing required fields");
            return;
        }

        try {
            EmailUtil.sendSubmissionIdEmail(to, submissionId);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(JsonUtil.toJsonObject(Map.of("status", "success")));
        } catch (Exception e) {
            handleError(response, "Failed to send submission ID email", e);
        }
    }

    private void handleSendPasswordReset(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        String to = request.getParameter("to");
        String resetToken = request.getParameter("resetToken");

        if (to == null || resetToken == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing required fields");
            return;
        }

        try {
            EmailUtil.sendPasswordResetEmail(to, resetToken);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(JsonUtil.toJsonObject(Map.of("status", "success")));
        } catch (Exception e) {
            handleError(response, "Failed to send password reset email", e);
        }
    }

    private void handleSendDecision(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        String to = request.getParameter("to");
        String submissionId = request.getParameter("submissionId");
        String decision = request.getParameter("decision");
        String comments = request.getParameter("comments");

        if (to == null || submissionId == null || decision == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing required fields");
            return;
        }

        try {
            EmailUtil.sendSubmissionDecisionEmail(to, submissionId, decision, comments);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(JsonUtil.toJsonObject(Map.of("status", "success")));
        } catch (Exception e) {
            handleError(response, "Failed to send decision email", e);
        }
    }

    private void handleError(HttpServletResponse response, String message, Exception e) 
            throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("details", e.getMessage());
        
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setContentType("application/json");
        response.getWriter().write(JsonUtil.toJsonObject(error));
    }
} 