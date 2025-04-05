package com.campusconf.controllers;

import com.campusconf.dao.ConferenceDAO;
import com.campusconf.dao.SubmissionDAO;
import com.campusconf.dao.impl.ConferenceDAOImpl;
import com.campusconf.dao.impl.SubmissionDAOImpl;
import com.campusconf.models.Conference;
import com.campusconf.models.Submission;
import com.campusconf.utils.ConstantsUtil;
import com.campusconf.utils.JsonUtil;
import com.campusconf.utils.LogUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/filter/*")
public class FilterServlet extends HttpServlet {
    private final ConferenceDAO conferenceDAO;
    private final SubmissionDAO submissionDAO;

    public FilterServlet() {
        this.conferenceDAO = new ConferenceDAOImpl();
        this.submissionDAO = new SubmissionDAOImpl();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
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
                case "/conferences":
                    handleConferenceFilter(request, response, session);
                    break;
                case "/submissions":
                    handleSubmissionFilter(request, response, session);
                    break;
                default:
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid filter operation");
            }
        } catch (Exception e) {
            handleError(response, "Error processing filter request", e);
        }
    }

    private void handleConferenceFilter(HttpServletRequest request, HttpServletResponse response, HttpSession session) 
            throws IOException, SQLException {
        // Get filter parameters
        String role = request.getParameter("role");
        String status = request.getParameter("status");
        String dateFilter = request.getParameter("date");

        // Get all conferences for the user
        List<Conference> conferences = conferenceDAO.findByUserId((Long) session.getAttribute("userId"));
        List<Conference> filteredResults = new ArrayList<>(conferences);

        // Apply role filter
        if (role != null && !role.trim().isEmpty()) {
            filteredResults = filterByRole(filteredResults, role, session);
        }

        // Apply status filter
        if (status != null && !status.trim().isEmpty()) {
            filteredResults = filterByStatus(filteredResults, status);
        }

        // Apply date filter
        if (dateFilter != null && !dateFilter.trim().isEmpty()) {
            filteredResults = filterByDate(filteredResults, dateFilter);
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
        int noOfRecords = filteredResults.size();
        int noOfPages = (int) Math.ceil(noOfRecords * 1.0 / recordsPerPage);
        int startIndex = (page - 1) * recordsPerPage;
        int endIndex = Math.min(startIndex + recordsPerPage, noOfRecords);

        // Get paginated results
        List<Conference> paginatedResults = filteredResults.subList(startIndex, endIndex);

        // Prepare response
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("totalResults", noOfRecords);
        result.put("currentPage", page);
        result.put("totalPages", noOfPages);
        result.put("results", paginatedResults);

        response.setContentType("application/json");
        response.getWriter().write(JsonUtil.toJsonObject(result));
    }

    private void handleSubmissionFilter(HttpServletRequest request, HttpServletResponse response, HttpSession session) 
            throws IOException, SQLException {
        // Get filter parameters
        String status = request.getParameter("status");
        String dateFilter = request.getParameter("date");
        String conferenceId = request.getParameter("conferenceId");

        // Get all submissions for the user
        List<Submission> submissions = submissionDAO.findByUserId((Long) session.getAttribute("userId"));
        List<Submission> filteredResults = new ArrayList<>(submissions);

        // Apply status filter
        if (status != null && !status.trim().isEmpty()) {
            filteredResults = filterSubmissionsByStatus(filteredResults, status);
        }

        // Apply date filter
        if (dateFilter != null && !dateFilter.trim().isEmpty()) {
            filteredResults = filterSubmissionsByDate(filteredResults, dateFilter);
        }

        // Apply conference filter
        if (conferenceId != null && !conferenceId.trim().isEmpty()) {
            filteredResults = filterSubmissionsByConference(filteredResults, Long.parseLong(conferenceId));
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
        int noOfRecords = filteredResults.size();
        int noOfPages = (int) Math.ceil(noOfRecords * 1.0 / recordsPerPage);
        int startIndex = (page - 1) * recordsPerPage;
        int endIndex = Math.min(startIndex + recordsPerPage, noOfRecords);

        // Get paginated results
        List<Submission> paginatedResults = filteredResults.subList(startIndex, endIndex);

        // Prepare response
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("totalResults", noOfRecords);
        result.put("currentPage", page);
        result.put("totalPages", noOfPages);
        result.put("results", paginatedResults);

        response.setContentType("application/json");
        response.getWriter().write(JsonUtil.toJsonObject(result));
    }

    private List<Conference> filterByRole(List<Conference> conferences, String role, HttpSession session) throws SQLException {
        final Long userId = (Long) session.getAttribute("userId");
        final List<Conference> authorConferences = new ArrayList<>();
        final List<Conference> committeeConferences = new ArrayList<>();

        // Pre-fetch conferences for Author and Committee roles
        if ("Author".equals(role) || "PC".equals(role) || "SC".equals(role)) {
            if ("Author".equals(role)) {
                authorConferences.addAll(conferenceDAO.findByAuthorId(userId));
            } else {
                committeeConferences.addAll(conferenceDAO.findByCommitteeMemberId(userId));
            }
        }

        return conferences.stream()
                .filter(conf -> {
                    switch (role) {
                        case "President":
                            return conf.getPresidentId().equals(userId);
                        case "Author":
                            return authorConferences.contains(conf);
                        case "PC":
                        case "SC":
                            return committeeConferences.contains(conf);
                        default:
                            return true;
                    }
                })
                .toList();
    }

    private List<Conference> filterByStatus(List<Conference> conferences, String status) {
        return conferences.stream()
                .filter(conf -> status.equals(conf.getStatus()))
                .toList();
    }

    private List<Conference> filterByDate(List<Conference> conferences, String dateFilter) {
        LocalDate today = LocalDate.now();
        return conferences.stream()
                .filter(conf -> {
                    switch (dateFilter) {
                        case "today":
                            return conf.getCreationDate().toLocalDateTime().toLocalDate().equals(today);
                        case "week":
                            return conf.getCreationDate().toLocalDateTime().toLocalDate()
                                    .isAfter(today.minusWeeks(1));
                        case "month":
                            return conf.getCreationDate().toLocalDateTime().toLocalDate()
                                    .isAfter(today.minusMonths(1));
                        case "year":
                            return conf.getCreationDate().toLocalDateTime().toLocalDate()
                                    .isAfter(today.minusYears(1));
                        default:
                            return true;
                    }
                })
                .toList();
    }

    private List<Submission> filterSubmissionsByStatus(List<Submission> submissions, String status) {
        return submissions.stream()
                .filter(sub -> status.equals(sub.getStatus()))
                .toList();
    }

    private List<Submission> filterSubmissionsByDate(List<Submission> submissions, String dateFilter) {
        LocalDate today = LocalDate.now();
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

    private List<Submission> filterSubmissionsByConference(List<Submission> submissions, Long conferenceId) {
        return submissions.stream()
                .filter(sub -> conferenceId.equals(sub.getConferenceId()))
                .toList();
    }

    private void handleError(HttpServletResponse response, String message, Exception e) 
            throws IOException {
        LogUtil.error(message, e);
        
        Map<String, Object> error = new HashMap<>();
        error.put("status", "error");
        error.put("message", message);
        error.put("details", e.getMessage());
        
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setContentType("application/json");
        response.getWriter().write(JsonUtil.toJsonObject(error));
    }
} 