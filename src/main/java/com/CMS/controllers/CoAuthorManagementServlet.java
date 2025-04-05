package com.CMS.controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.campusconf.models.Submission;
import com.campusconf.models.SubmissionAuthor;
import com.campusconf.models.User;
import com.campusconf.utils.DatabaseUtil;
import com.campusconf.utils.EmailUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/team/manage")
public class CoAuthorManagementServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        HttpSession session = request.getSession();
        User currentUser = (User) session.getAttribute("user");
        
        if (currentUser == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String submissionId = request.getParameter("submissionId");
        if (submissionId == null || submissionId.trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/dashboard");
            return;
        }

        try (Connection conn = DatabaseUtil.getConnection()) {
            // Verify user is the corresponding author
            String checkAuthorSql = 
                "SELECT COUNT(*) FROM submission_authors " +
                "WHERE submission_id = ? AND user_id = ? AND corresponding_author = true";
            
            PreparedStatement checkStmt = conn.prepareStatement(checkAuthorSql);
            checkStmt.setString(1, submissionId);
            checkStmt.setLong(2, currentUser.getUserId());
            ResultSet checkRs = checkStmt.executeQuery();
            checkRs.next();

            if (checkRs.getInt(1) == 0) {
                response.sendRedirect(request.getContextPath() + "/dashboard?error=unauthorized");
                return;
            }

            // Get submission details
            String submissionSql = 
                "SELECT * FROM submissions WHERE submission_id = ?";
            PreparedStatement submissionStmt = conn.prepareStatement(submissionSql);
            submissionStmt.setString(1, submissionId);
            ResultSet submissionRs = submissionStmt.executeQuery();
            
            if (!submissionRs.next()) {
                response.sendRedirect(request.getContextPath() + "/dashboard?error=submission_not_found");
                return;
            }

            // Get all co-authors
            String authorsSql = 
                "SELECT sa.*, u.first_name, u.last_name, u.email " +
                "FROM submission_authors sa " +
                "JOIN users u ON sa.user_id = u.user_id " +
                "WHERE sa.submission_id = ?";
            
            PreparedStatement authorsStmt = conn.prepareStatement(authorsSql);
            authorsStmt.setString(1, submissionId);
            ResultSet authorsRs = authorsStmt.executeQuery();

            List<SubmissionAuthor> coAuthors = new ArrayList<>();
            while (authorsRs.next()) {
                SubmissionAuthor author = new SubmissionAuthor();
                author.setId(authorsRs.getLong("id"));
                author.setSubmissionId(authorsRs.getString("submission_id"));
                author.setUserId(authorsRs.getLong("user_id"));
                author.setCorrespondingAuthor(authorsRs.getBoolean("corresponding_author"));
                author.setCreatedAt(authorsRs.getTimestamp("created_at"));
                author.setUpdatedAt(authorsRs.getTimestamp("updated_at"));
                coAuthors.add(author);
            }

            // Set attributes for JSP
            request.setAttribute("submissionId", submissionId);
            request.setAttribute("submissionTitle", submissionRs.getString("title"));
            request.setAttribute("coAuthors", coAuthors);

            // Forward to management page
            request.getRequestDispatcher("/WEB-INF/views/team/manage.jsp").forward(request, response);

        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect(request.getContextPath() + "/dashboard?error=system_error");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        HttpSession session = request.getSession();
        User currentUser = (User) session.getAttribute("user");
        
        if (currentUser == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String submissionId = request.getParameter("submissionId");
        String action = request.getParameter("action");
        String authorId = request.getParameter("authorId");

        if (submissionId == null || action == null || authorId == null) {
            response.sendRedirect(request.getContextPath() + "/dashboard?error=invalid_request");
            return;
        }

        try (Connection conn = DatabaseUtil.getConnection()) {
            // Verify user is the corresponding author
            String checkAuthorSql = 
                "SELECT COUNT(*) FROM submission_authors " +
                "WHERE submission_id = ? AND user_id = ? AND corresponding_author = true";
            
            PreparedStatement checkStmt = conn.prepareStatement(checkAuthorSql);
            checkStmt.setString(1, submissionId);
            checkStmt.setLong(2, currentUser.getUserId());
            ResultSet checkRs = checkStmt.executeQuery();
            checkRs.next();

            if (checkRs.getInt(1) == 0) {
                response.sendRedirect(request.getContextPath() + "/dashboard?error=unauthorized");
                return;
            }

            // Check submission deadline
            String deadlineSql = 
                "SELECT c.submission_deadline, c.extension_date " +
                "FROM submissions s " +
                "JOIN conferences c ON s.conference_id = c.conference_id " +
                "WHERE s.submission_id = ?";
            
            PreparedStatement deadlineStmt = conn.prepareStatement(deadlineSql);
            deadlineStmt.setString(1, submissionId);
            ResultSet deadlineRs = deadlineStmt.executeQuery();
            deadlineRs.next();

            java.sql.Date submissionDeadline = deadlineRs.getDate("submission_deadline");
            java.sql.Date extensionDate = deadlineRs.getDate("extension_date");
            java.sql.Date currentDate = new java.sql.Date(System.currentTimeMillis());

            if (currentDate.after(extensionDate != null ? extensionDate : submissionDeadline)) {
                response.sendRedirect(request.getContextPath() + "/dashboard?error=deadline_passed");
                return;
            }

            if (action.equals("remove")) {
                // Remove co-author
                String removeSql = 
                    "DELETE FROM submission_authors " +
                    "WHERE submission_id = ? AND user_id = ? AND corresponding_author = false";
                
                PreparedStatement removeStmt = conn.prepareStatement(removeSql);
                removeStmt.setString(1, submissionId);
                removeStmt.setLong(2, Long.parseLong(authorId));
                removeStmt.executeUpdate();

                // Get removed author's email for notification
                String getAuthorSql = 
                    "SELECT u.email, u.first_name, u.last_name " +
                    "FROM users u " +
                    "WHERE u.user_id = ?";
                
                PreparedStatement authorStmt = conn.prepareStatement(getAuthorSql);
                authorStmt.setLong(1, Long.parseLong(authorId));
                ResultSet authorRs = authorStmt.executeQuery();
                
                if (authorRs.next()) {
                    String authorEmail = authorRs.getString("email");
                    String authorName = authorRs.getString("first_name") + " " + 
                                     authorRs.getString("last_name");
                    
                    // Send email notification
                    String subject = "Removed from Submission Team";
                    String body = String.format(
                        "Dear %s,\n\n" +
                        "You have been removed from the submission team.\n\n" +
                        "Best regards,\n" +
                        "CampusConf Team",
                        authorName
                    );
                    
                    EmailUtil.sendEmail(authorEmail, subject, body);
                }
            }

            response.sendRedirect(request.getContextPath() + "/team/manage?submissionId=" + submissionId + "&success=" + action);

        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect(request.getContextPath() + "/dashboard?error=system_error");
        }
    }
} 