package com.campusconf.controllers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

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
import com.campusconf.models.User;
import com.campusconf.utils.DatabaseUtil;

@WebServlet(urlPatterns = {"/dashboard", "/dashboard/*"})
public class DashboardServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private ConferenceDAO conferenceDAO;
    private SubmissionDAO submissionDAO;
    private CommitteeMemberDAO committeeMemberDAO;

    @Override
    public void init() throws ServletException {
        try {
            conferenceDAO = new ConferenceDAOImpl();
            submissionDAO = new SubmissionDAOImpl();
            committeeMemberDAO = new CommitteeMemberDAOImpl();
        } catch (Exception e) {
            throw new ServletException("Failed to initialize DAOs", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        // Check if user is authenticated
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        User user = (User) session.getAttribute("user");
        if (user == null || user.getUserId() == null) {
            session.invalidate();
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        Long userId = user.getUserId();

        try {
            // Get filter parameters
            String filterStatus = request.getParameter("filterStatus");
            String filterDate = request.getParameter("filterDate");

            // Get pagination parameters
            int page = 1;
            int recordsPerPage = 10;
            String pageStr = request.getParameter("page");
            if (pageStr != null && !pageStr.isEmpty()) {
                page = Integer.parseInt(pageStr);
            }

            // Get all conferences for the user
            List<Conference> conferences = conferenceDAO.findByUserId(userId);
            
            // Initialize the roles map even if there are no conferences
            Map<Long, String> conferenceRoles = new HashMap<>();
            
            // Only process roles if there are conferences
            if (!conferences.isEmpty()) {
                // Get committee memberships
                List<CommitteeMember> committeeMembers = committeeMemberDAO.findByUserId(userId);
                
                // Determine role for each conference
                for (Conference conf : conferences) {
                    String role = "USER";
                    
                    // Check if user is president
                    if (userId.equals(conf.getPresidentId())) {
                        role = "PRESIDENT";
                    } else {
                        // Check if user is a committee member
                        for (CommitteeMember member : committeeMembers) {
                            if (member.getConferenceId().equals(conf.getConferenceId())) {
                                role = member.getCommitteeType() + (member.isResponsible() ? "_RESP" : "_MEMBER");
                                break;
                            }
                        }
                        
                        // If not a committee member, check if user is an author
                        if (role.equals("USER")) {
                            List<Submission> submissions = submissionDAO.findByConferenceId(conf.getConferenceId());
                            for (Submission sub : submissions) {
                                if (userId.equals(sub.getCorrespondingAuthorId())) {
                                    role = "AUTHOR_CP";
                                    break;
                                }
                            }
                            if (role.equals("USER")) {
                                for (Submission sub : submissions) {
                                    if (sub.getAuthors().stream().anyMatch(author -> userId.equals(author.getUserId()))) {
                                        role = "AUTHOR";
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    conferenceRoles.put(conf.getConferenceId(), role);
                }

                // Apply status filter
                if (filterStatus != null && !filterStatus.isEmpty()) {
                    conferences = new ArrayList<>(conferences.stream()
                        .filter(conf -> filterStatus.equals(conf.getStatus()))
                        .toList());
                }

                // Sort by date if requested
                if ("asc".equals(filterDate)) {
                    List<Conference> sortedConferences = new ArrayList<>(conferences);
                    sortedConferences.sort((c1, c2) -> c1.getCreationDate().compareTo(c2.getCreationDate()));
                    conferences = sortedConferences;
                } else if ("desc".equals(filterDate)) {
                    List<Conference> sortedConferences = new ArrayList<>(conferences);
                    sortedConferences.sort((c1, c2) -> c2.getCreationDate().compareTo(c1.getCreationDate()));
                    conferences = sortedConferences;
                }

                // Calculate pagination
                int noOfRecords = conferences.size();
                int noOfPages = (int) Math.ceil(noOfRecords * 1.0 / recordsPerPage);
                int startIndex = (page - 1) * recordsPerPage;
                int endIndex = Math.min(startIndex + recordsPerPage, noOfRecords);

                // Get paginated conferences
                List<Conference> paginatedConferences = conferences.subList(startIndex, endIndex);
                
                // Set pagination attributes
                request.setAttribute("currentPage", page);
                request.setAttribute("noOfPages", noOfPages);
                request.setAttribute("recordsPerPage", recordsPerPage);
                
                // Set the paginated conferences
                request.setAttribute("conferences", paginatedConferences);
            } else {
                // Set empty list for new users
                request.setAttribute("conferences", conferences);
            }

            // Set attributes for the view
            request.setAttribute("conferenceRoles", conferenceRoles);
            request.setAttribute("filterStatus", filterStatus);
            request.setAttribute("filterDate", filterDate);
            request.setAttribute("activePage", "dashboard");
            request.setAttribute("user", user);
            request.setAttribute("content", "index.jsp");

            // Forward to the layout view
            request.getRequestDispatcher("/WEB-INF/views/dashboard/layout.jsp").forward(request, response);
        } catch (SQLException e) {
            request.setAttribute("error", "Database error occurred while fetching dashboard data.");
            request.getRequestDispatcher("/WEB-INF/views/error.jsp").forward(request, response);
        }
    }
} 