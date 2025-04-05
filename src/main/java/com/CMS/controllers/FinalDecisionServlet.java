package com.CMS.controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.campusconf.models.Review;
import com.campusconf.models.Submission;
import com.campusconf.models.User;
import com.campusconf.utils.DatabaseUtil;
import com.campusconf.utils.EmailUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/review/final-decision")
public class FinalDecisionServlet extends HttpServlet {
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
            // Verify user is SC Resp
            String checkRoleSql = 
                "SELECT cm.committee_type, cm.is_responsible " +
                "FROM committee_members cm " +
                "WHERE cm.user_id = ? AND cm.committee_type = 'SC' AND cm.is_responsible = true";
            
            PreparedStatement checkStmt = conn.prepareStatement(checkRoleSql);
            checkStmt.setLong(1, currentUser.getUserId());
            ResultSet checkRs = checkStmt.executeQuery();
            
            if (!checkRs.next()) {
                response.sendRedirect(request.getContextPath() + "/dashboard?error=unauthorized");
                return;
            }

            // Get submission details
            String submissionSql = 
                "SELECT s.*, c.name as conference_name " +
                "FROM submissions s " +
                "JOIN conferences c ON s.conference_id = c.conference_id " +
                "WHERE s.submission_id = ?";
            
            PreparedStatement submissionStmt = conn.prepareStatement(submissionSql);
            submissionStmt.setString(1, submissionId);
            ResultSet submissionRs = submissionStmt.executeQuery();
            
            if (!submissionRs.next()) {
                response.sendRedirect(request.getContextPath() + "/dashboard?error=submission_not_found");
                return;
            }

            // Check if all reviews are completed
            String checkReviewsSql = 
                "SELECT COUNT(*) as total, " +
                "       SUM(CASE WHEN review_status = 'COMPLETED' THEN 1 ELSE 0 END) as completed " +
                "FROM reviews " +
                "WHERE submission_id = ?";
            
            PreparedStatement reviewsStmt = conn.prepareStatement(checkReviewsSql);
            reviewsStmt.setString(1, submissionId);
            ResultSet reviewsRs = reviewsStmt.executeQuery();
            reviewsRs.next();

            int totalReviews = reviewsRs.getInt("total");
            int completedReviews = reviewsRs.getInt("completed");

            if (completedReviews < totalReviews) {
                response.sendRedirect(request.getContextPath() + "/dashboard?error=reviews_incomplete");
                return;
            }

            // Get all reviews
            String getReviewsSql = 
                "SELECT r.*, u.first_name, u.last_name, u.email " +
                "FROM reviews r " +
                "JOIN users u ON r.reviewer_id = u.user_id " +
                "WHERE r.submission_id = ?";
            
            PreparedStatement getReviewsStmt = conn.prepareStatement(getReviewsSql);
            getReviewsStmt.setString(1, submissionId);
            ResultSet getReviewsRs = getReviewsStmt.executeQuery();

            List<Review> reviews = new ArrayList<>();
            while (getReviewsRs.next()) {
                Review review = new Review();
                review.setReviewId(getReviewsRs.getLong("review_id"));
                review.setSubmissionId(getReviewsRs.getString("submission_id"));
                review.setReviewerId(getReviewsRs.getLong("reviewer_id"));
                review.setReviewStatus(getReviewsRs.getString("review_status"));
                review.setReviewDecision(getReviewsRs.getString("review_decision"));
                review.setReviewComments(getReviewsRs.getString("review_comments"));
                review.setCreatedAt(getReviewsRs.getTimestamp("created_at"));
                review.setUpdatedAt(getReviewsRs.getTimestamp("updated_at"));
                reviews.add(review);
            }

            // Set attributes for JSP
            request.setAttribute("submissionId", submissionId);
            request.setAttribute("submissionTitle", submissionRs.getString("title"));
            request.setAttribute("conferenceName", submissionRs.getString("conference_name"));
            request.setAttribute("reviews", reviews);

            // Forward to final decision page
            request.getRequestDispatcher("/WEB-INF/views/review/final-decision.jsp").forward(request, response);

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
        String finalDecision = request.getParameter("finalDecision");
        String comments = request.getParameter("comments");

        if (submissionId == null || finalDecision == null) {
            response.sendRedirect(request.getContextPath() + "/dashboard?error=invalid_request");
            return;
        }

        try (Connection conn = DatabaseUtil.getConnection()) {
            // Verify user is SC Resp
            String checkRoleSql = 
                "SELECT cm.committee_type, cm.is_responsible " +
                "FROM committee_members cm " +
                "WHERE cm.user_id = ? AND cm.committee_type = 'SC' AND cm.is_responsible = true";
            
            PreparedStatement checkStmt = conn.prepareStatement(checkRoleSql);
            checkStmt.setLong(1, currentUser.getUserId());
            ResultSet checkRs = checkStmt.executeQuery();
            
            if (!checkRs.next()) {
                response.sendRedirect(request.getContextPath() + "/dashboard?error=unauthorized");
                return;
            }

            // Check if all reviews are completed
            String checkReviewsSql = 
                "SELECT COUNT(*) as total, " +
                "       SUM(CASE WHEN review_status = 'COMPLETED' THEN 1 ELSE 0 END) as completed " +
                "FROM reviews " +
                "WHERE submission_id = ?";
            
            PreparedStatement reviewsStmt = conn.prepareStatement(checkReviewsSql);
            reviewsStmt.setString(1, submissionId);
            ResultSet reviewsRs = reviewsStmt.executeQuery();
            reviewsRs.next();

            int totalReviews = reviewsRs.getInt("total");
            int completedReviews = reviewsRs.getInt("completed");

            if (completedReviews < totalReviews) {
                response.sendRedirect(request.getContextPath() + "/dashboard?error=reviews_incomplete");
                return;
            }

            // Update submission status
            String updateSubmissionSql = 
                "UPDATE submissions " +
                "SET status = ?, " +
                "    updated_at = ? " +
                "WHERE submission_id = ?";
            
            PreparedStatement updateStmt = conn.prepareStatement(updateSubmissionSql);
            updateStmt.setString(1, finalDecision);
            updateStmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            updateStmt.setString(3, submissionId);
            updateStmt.executeUpdate();

            // Get submission details and authors for email
            String getDetailsSql = 
                "SELECT s.title, c.name as conference_name, " +
                "       GROUP_CONCAT(DISTINCT u.email) as author_emails, " +
                "       GROUP_CONCAT(DISTINCT CONCAT(u.first_name, ' ', u.last_name)) as author_names " +
                "FROM submissions s " +
                "JOIN conferences c ON s.conference_id = c.conference_id " +
                "JOIN submission_authors sa ON s.submission_id = sa.submission_id " +
                "JOIN users u ON sa.user_id = u.user_id " +
                "WHERE s.submission_id = ? " +
                "GROUP BY s.submission_id";
            
            PreparedStatement detailsStmt = conn.prepareStatement(getDetailsSql);
            detailsStmt.setString(1, submissionId);
            ResultSet detailsRs = detailsStmt.executeQuery();
            detailsRs.next();

            String submissionTitle = detailsRs.getString("title");
            String conferenceName = detailsRs.getString("conference_name");
            String[] authorEmails = detailsRs.getString("author_emails").split(",");
            String[] authorNames = detailsRs.getString("author_names").split(",");

            // Send email notifications to all authors
            String subject = "Final Decision on Your Submission";
            String body = String.format(
                "Dear Author(s),\n\n" +
                "The final decision has been made on your submission:\n\n" +
                "Conference: %s\n" +
                "Submission Title: %s\n" +
                "Submission ID: %s\n" +
                "Final Decision: %s\n\n" +
                "Comments from the Review Committee:\n%s\n\n" +
                "Best regards,\n" +
                "CampusConf Team",
                conferenceName, submissionTitle, submissionId,
                finalDecision, comments != null ? comments : "No additional comments."
            );

            for (String authorEmail : authorEmails) {
                EmailUtil.sendEmail(authorEmail.trim(), subject, body);
            }

            response.sendRedirect(request.getContextPath() + "/dashboard?success=final_decision_made");

        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect(request.getContextPath() + "/dashboard?error=system_error");
        }
    }
} 