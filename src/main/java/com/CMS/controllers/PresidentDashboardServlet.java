package com.campusconf.controllers;

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
import com.campusconf.dao.CommitteeMemberDAO;
import com.campusconf.dao.impl.CommitteeMemberDAOImpl;
import com.campusconf.models.Conference;
import com.campusconf.models.Submission;
import com.campusconf.models.CommitteeMember;
import com.campusconf.utils.DatabaseUtil;

@WebServlet("/dashboard/president/*")
public class PresidentDashboardServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private ConferenceDAO conferenceDAO;
    private SubmissionDAO submissionDAO;
    private CommitteeMemberDAO committeeDAO;

    @Override
    public void init() throws ServletException {
        try {
            conferenceDAO = new ConferenceDAOImpl();
            submissionDAO = new SubmissionDAOImpl();
            committeeDAO = new CommitteeMemberDAOImpl();
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
        if (!"President".equals(role)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        Long userId = (Long) session.getAttribute("userId");

        try {
            // Get conferences where user is president
            List<Conference> conferences = conferenceDAO.findByPresidentId(userId);
            
            // Get submissions for all conferences
            List<Submission> submissions = submissionDAO.findByConferenceIds(
                conferences.stream()
                    .map(Conference::getConferenceId)
                    .toList()
            );

            // Get committee members for all conferences
            List<CommitteeMember> committeeMembers = committeeDAO.findByConferenceIds(
                conferences.stream()
                    .map(Conference::getConferenceId)
                    .toList()
            );

            // Get filter parameters
            String filterStatus = request.getParameter("filterStatus");
            String filterDate = request.getParameter("filterDate");
            String filterConference = request.getParameter("filterConference");

            // Apply filters if provided
            if (filterStatus != null && !filterStatus.trim().isEmpty()) {
                submissions = filterSubmissionsByStatus(submissions, filterStatus);
            }
            if (filterDate != null && !filterDate.trim().isEmpty()) {
                submissions = filterSubmissionsByDate(submissions, filterDate);
            }
            if (filterConference != null && !filterConference.trim().isEmpty()) {
                submissions = filterSubmissionsByConference(submissions, Long.parseLong(filterConference));
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
            request.setAttribute("committeeMembers", committeeMembers);
            request.setAttribute("submissions", paginatedSubmissions);
            request.setAttribute("currentPage", page);
            request.setAttribute("noOfPages", noOfPages);
            request.setAttribute("recordsPerPage", recordsPerPage);
            request.setAttribute("filterStatus", filterStatus);
            request.setAttribute("filterDate", filterDate);
            request.setAttribute("filterConference", filterConference);

            request.getRequestDispatcher("/WEB-INF/views/dashboard/president.jsp").forward(request, response);
        } catch (SQLException e) {
            request.setAttribute("error", "Database error occurred while fetching president dashboard data.");
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
        if (!"President".equals(role)) {
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
                case "manageCommittee":
                    manageCommittee(request, response, session);
                    break;
                case "manageSubmissions":
                    manageSubmissions(request, response, session);
                    break;
                case "updateConference":
                    updateConference(request, response, session);
                    break;
                default:
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            }
        } catch (SQLException e) {
            request.setAttribute("error", "Database error occurred while processing the request.");
            request.getRequestDispatcher("/WEB-INF/views/error.jsp").forward(request, response);
        }
    }

    private void manageCommittee(HttpServletRequest request, HttpServletResponse response, HttpSession session) 
            throws SQLException, ServletException, IOException {
        String conferenceId = request.getParameter("conferenceId");
        if (conferenceId == null || conferenceId.trim().isEmpty()) {
            request.setAttribute("error", "Conference ID is required");
            request.getRequestDispatcher("/WEB-INF/views/dashboard/president.jsp").forward(request, response);
            return;
        }

        response.sendRedirect(request.getContextPath() + "/conference/committee/" + conferenceId);
    }

    private void manageSubmissions(HttpServletRequest request, HttpServletResponse response, HttpSession session) 
            throws SQLException, ServletException, IOException {
        String conferenceId = request.getParameter("conferenceId");
        if (conferenceId == null || conferenceId.trim().isEmpty()) {
            request.setAttribute("error", "Conference ID is required");
            request.getRequestDispatcher("/WEB-INF/views/dashboard/president.jsp").forward(request, response);
            return;
        }

        response.sendRedirect(request.getContextPath() + "/conference/submissions/" + conferenceId);
    }

    private void updateConference(HttpServletRequest request, HttpServletResponse response, HttpSession session) 
            throws SQLException, ServletException, IOException {
        String conferenceId = request.getParameter("conferenceId");
        if (conferenceId == null || conferenceId.trim().isEmpty()) {
            request.setAttribute("error", "Conference ID is required");
            request.getRequestDispatcher("/WEB-INF/views/dashboard/president.jsp").forward(request, response);
            return;
        }

        response.sendRedirect(request.getContextPath() + "/conference/modify/" + conferenceId);
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

    private List<Submission> filterSubmissionsByConference(List<Submission> submissions, Long conferenceId) {
        return submissions.stream()
                .filter(sub -> conferenceId.equals(sub.getConferenceId()))
                .toList();
    }
} 