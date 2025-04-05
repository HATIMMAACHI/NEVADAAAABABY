package com.CMS.controllers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.campusconf.dao.ConferenceDAO;
import com.campusconf.dao.impl.ConferenceDAOImpl;
import com.campusconf.dao.SubmissionDAO;
import com.campusconf.dao.impl.SubmissionDAOImpl;
import com.campusconf.models.Conference;
import com.campusconf.models.Submission;
import com.campusconf.utils.DatabaseUtil;

@WebServlet("/dashboard/author/*")
public class AuthorDashboardServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private ConferenceDAO conferenceDAO;
    private SubmissionDAO submissionDAO;

    @Override
    public void init() throws ServletException {
        try {
            conferenceDAO = new ConferenceDAOImpl();
            submissionDAO = new SubmissionDAOImpl();
        } catch (Exception e) {
            throw new ServletException("Failed to initialize DAOs", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String role = (String) session.getAttribute("role");
        if (!"Author".equals(role) && !"Author CP".equals(role)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        Long userId = (Long) session.getAttribute("userId");

        try {
            // Get conferences where user is an author
            List<Conference> conferences = conferenceDAO.findByAuthorId(userId);
            
            // Get user's submissions
            List<Submission> submissions = submissionDAO.findByUserId(userId);

            // Get filter parameters
            String filterStatus = request.getParameter("filterStatus");
            String filterDate = request.getParameter("filterDate");

            // Apply filters if provided
            if (filterStatus != null && !filterStatus.trim().isEmpty()) {
                submissions = filterSubmissionsByStatus(submissions, filterStatus);
            }
            if (filterDate != null && !filterDate.trim().isEmpty()) {
                submissions = filterSubmissionsByDate(submissions, filterDate);
            }

            // Set pagination parameters
            int page = 1;
            int recordsPerPage = 10;
            try {
                page = Integer.parseInt(request.getParameter("page"));
                recordsPerPage = Integer.parseInt(request.getParameter("recordsPerPage"));
            } catch (NumberFormatException e) {
                // Use default values
            }

            // Calculate pagination
            int noOfRecords = submissions.size();
            int noOfPages = (int) Math.ceil(noOfRecords * 1.0 / recordsPerPage);
            int startIndex = (page - 1) * recordsPerPage;
            int endIndex = Math.min(startIndex + recordsPerPage, noOfRecords);

            // Get paginated submissions
            List<Submission> paginatedSubmissions = submissions.subList(startIndex, endIndex);

            // Set attributes for the view
            request.setAttribute("conferences", conferences);
            request.setAttribute("submissions", paginatedSubmissions);
            request.setAttribute("currentPage", page);
            request.setAttribute("noOfPages", noOfPages);
            request.setAttribute("recordsPerPage", recordsPerPage);
            request.setAttribute("filterStatus", filterStatus);
            request.setAttribute("filterDate", filterDate);

            request.getRequestDispatcher("/WEB-INF/views/dashboard/author.jsp").forward(request, response);
        } catch (SQLException e) {
            request.setAttribute("error", "Database error occurred while fetching author dashboard data.");
            request.getRequestDispatcher("/WEB-INF/views/error.jsp").forward(request, response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String role = (String) session.getAttribute("role");
        if (!"Author".equals(role) && !"Author CP".equals(role)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String action = request.getParameter("action");
        if (action == null || action.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        try {
            switch (action) {
                case "joinTeam":
                    joinTeam(request, response, session);
                    break;
                case "submitArticle":
                    submitArticle(request, response, session);
                    break;
                case "updateSubmission":
                    updateSubmission(request, response, session);
                    break;
                default:
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            }
        } catch (SQLException e) {
            request.setAttribute("error", "Database error occurred while processing the request.");
            request.getRequestDispatcher("/WEB-INF/views/error.jsp").forward(request, response);
        }
    }

    private void joinTeam(HttpServletRequest request, HttpServletResponse response, HttpSession session) 
            throws SQLException, ServletException, IOException {
        String submissionId = request.getParameter("submissionId");
        if (submissionId == null || submissionId.trim().isEmpty()) {
            request.setAttribute("error", "Submission ID is required");
            request.getRequestDispatcher("/WEB-INF/views/dashboard/author.jsp").forward(request, response);
            return;
        }

        Long userId = (Long) session.getAttribute("userId");
        if (submissionDAO.joinTeam(submissionId, userId)) {
            session.setAttribute("successMessage", "Successfully joined the team");
        } else {
            request.setAttribute("error", "Failed to join the team");
            request.getRequestDispatcher("/WEB-INF/views/dashboard/author.jsp").forward(request, response);
            return;
        }

        response.sendRedirect(request.getContextPath() + "/dashboard/author");
    }

    private void submitArticle(HttpServletRequest request, HttpServletResponse response, HttpSession session) 
            throws SQLException, ServletException, IOException {
        // Implementation for article submission
        // This will be handled by a separate servlet
        response.sendRedirect(request.getContextPath() + "/submission/create");
    }

    private void updateSubmission(HttpServletRequest request, HttpServletResponse response, HttpSession session) 
            throws SQLException, ServletException, IOException {
        // Implementation for submission update
        // This will be handled by a separate servlet
        String submissionId = request.getParameter("submissionId");
        if (submissionId == null || submissionId.trim().isEmpty()) {
            request.setAttribute("error", "Submission ID is required");
            request.getRequestDispatcher("/WEB-INF/views/dashboard/author.jsp").forward(request, response);
            return;
        }

        response.sendRedirect(request.getContextPath() + "/submission/update/" + submissionId);
    }

    private List<Submission> filterSubmissionsByStatus(List<Submission> submissions, String status) {
        return submissions.stream()
                .filter(sub -> status.equals(sub.getStatus()))
                .toList();
    }

    private List<Submission> filterSubmissionsByDate(List<Submission> submissions, String dateFilter) {
        java.time.LocalDate today = java.time.LocalDate.now();
        return submissions.stream()
                .filter(sub -> {
                    switch (dateFilter) {
                        case "today":
                            return sub.getSubmissionDate().toLocalDateTime().toLocalDate().equals(today);
                        case "week":
                            return sub.getSubmissionDate().toLocalDateTime().toLocalDate()
                                    .isAfter(today.minusWeeks(1));
                        case "month":
                            return sub.getSubmissionDate().toLocalDateTime().toLocalDate()
                                    .isAfter(today.minusMonths(1));
                        case "year":
                            return sub.getSubmissionDate().toLocalDateTime().toLocalDate()
                                    .isAfter(today.minusYears(1));
                        default:
                            return true;
                    }
                })
                .toList();
    }
} 