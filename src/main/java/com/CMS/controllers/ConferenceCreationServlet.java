package com.campusconf.controllers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.sql.Timestamp;
import java.sql.Connection;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.mail.MessagingException;

import com.campusconf.dao.ConferenceDAO;
import com.campusconf.dao.impl.ConferenceDAOImpl;
import com.campusconf.dao.ConferenceTopicDAO;
import com.campusconf.dao.impl.ConferenceTopicDAOImpl;
import com.campusconf.models.Conference;
import com.campusconf.models.ConferenceTopic;
import com.campusconf.utils.ValidationUtils;
import com.campusconf.utils.DatabaseUtil;
import com.campusconf.dao.CommitteeMemberDAO;
import com.campusconf.dao.impl.CommitteeMemberDAOImpl;
import com.campusconf.models.CommitteeMember;
import com.campusconf.dao.UserDAO;
import com.campusconf.dao.impl.UserDAOImpl;
import com.campusconf.models.User;
import com.campusconf.utils.DatabaseConnection;
import com.campusconf.utils.EmailUtil;
import com.campusconf.utils.StringUtil;

@WebServlet("/conference/create")
public class ConferenceCreationServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private ConferenceDAO conferenceDAO;
    private ConferenceTopicDAO topicDAO;
    private CommitteeMemberDAO committeeDAO;
    private UserDAO userDAO;

    @Override
    public void init() throws ServletException {
        try {
            conferenceDAO = new ConferenceDAOImpl();
            topicDAO = new ConferenceTopicDAOImpl();
            committeeDAO = new CommitteeMemberDAOImpl();
            userDAO = new UserDAOImpl(DatabaseConnection.getConnection());
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

        request.getRequestDispatcher("/WEB-INF/views/conference/create.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        Long userId = (Long) session.getAttribute("userId");
        Map<String, String> errors = new HashMap<>();
        Connection conn = null;
        
        try {
            // Get form data
            String name = request.getParameter("name");
            String acronym = request.getParameter("acronym");
            String theme = request.getParameter("theme");
            String type = request.getParameter("type");
            String website = request.getParameter("website");
            String startDate = request.getParameter("startDate");
            String endDate = request.getParameter("endDate");
            String location = request.getParameter("location");
            String submissionDeadline = request.getParameter("submissionDeadline");
            String[] topics = request.getParameterValues("topics[]");
            String[] subtopics = request.getParameterValues("subtopics[]");
            String[] committeeEmails = request.getParameterValues("committeeEmails[]");
            String[] committeeNames = request.getParameterValues("committeeNames[]");
            String committeeName = request.getParameter("committeeName");

            // Validate input
            if (name == null || name.trim().isEmpty()) {
                errors.put("name", "Conference name is required");
            }
            if (acronym == null || acronym.trim().isEmpty()) {
                errors.put("acronym", "Acronym is required");
            }
            if (theme == null || theme.trim().isEmpty()) {
                errors.put("theme", "Theme is required");
            }
            if (type == null || !isValidConferenceType(type)) {
                errors.put("type", "Invalid conference type");
            }
            if (website != null && !website.trim().isEmpty() && !ValidationUtils.isValidUrl(website)) {
                errors.put("website", "Invalid website URL");
            }
            if (startDate == null || startDate.trim().isEmpty()) {
                errors.put("startDate", "Start date is required");
            }
            if (endDate == null || endDate.trim().isEmpty()) {
                errors.put("endDate", "End date is required");
            }
            if (location == null || location.trim().isEmpty()) {
                errors.put("location", "Location is required");
            }
            if (submissionDeadline == null || submissionDeadline.trim().isEmpty()) {
                errors.put("submissionDeadline", "Submission deadline is required");
            }
            if (topics == null || topics.length == 0) {
                errors.put("topics", "At least one topic is required");
            }
            if (committeeName == null || committeeName.trim().isEmpty()) {
                errors.put("committeeName", "Committee name is required");
            }
            if (committeeEmails == null || committeeEmails.length == 0) {
                errors.put("committeeEmails", "At least one committee member is required");
            }

            if (!errors.isEmpty()) {
                request.setAttribute("errors", errors);
                request.getRequestDispatcher("/WEB-INF/views/conference/create.jsp").forward(request, response);
                return;
            }

            // Start transaction
            conn = DatabaseUtil.getConnection();
            conn.setAutoCommit(false);

            try {
                // Create conference
                Conference conference = new Conference();
                conference.setName(name);
                conference.setAcronym(acronym);
                conference.setTheme(theme);
                conference.setType(type);
                conference.setWebsite(website);
                conference.setStartDate(java.sql.Date.valueOf(startDate));
                conference.setEndDate(java.sql.Date.valueOf(endDate));
                conference.setLocation(location);
                conference.setSubmissionDeadline(java.sql.Date.valueOf(submissionDeadline));
                conference.setPresidentId(userId);
                conference.setCreationDate(new Timestamp(System.currentTimeMillis()));
                conference.setStatus("Ongoing");

                if (conferenceDAO.create(conference)) {
                    // Add topics and subtopics
                    for (int i = 0; i < topics.length; i++) {
                        if (topics[i] != null && !topics[i].trim().isEmpty()) {
                            // Create main topic
                            ConferenceTopic topic = new ConferenceTopic();
                            topic.setConferenceId(conference.getConferenceId());
                            topic.setTopicName(topics[i].trim());
                            topicDAO.create(topic);

                            // Create subtopic if exists
                            if (subtopics != null && i < subtopics.length && 
                                subtopics[i] != null && !subtopics[i].trim().isEmpty()) {
                                ConferenceTopic subtopic = new ConferenceTopic();
                                subtopic.setConferenceId(conference.getConferenceId());
                                subtopic.setTopicName(subtopics[i].trim());
                                subtopic.setParentTopicId(topic.getTopicId());
                                topicDAO.create(subtopic);
                            }
                        }
                    }

                    // Add committee members
                    if (committeeEmails != null && committeeNames != null) {
                        for (int i = 0; i < committeeEmails.length; i++) {
                            if (committeeEmails[i] != null && !committeeEmails[i].trim().isEmpty() &&
                                committeeNames[i] != null && !committeeNames[i].trim().isEmpty()) {
                                
                                String email = committeeEmails[i].trim();
                                String fullName = committeeNames[i].trim();
                                
                                // Try to find existing user
                                User user = userDAO.findByEmail(email);
                                
                                // If user doesn't exist, create a new one
                                if (user == null) {
                                    user = new User();
                                    user.setEmail(email);
                                    // Split full name into first and last name
                                    String[] nameParts = fullName.split("\\s+", 2);
                                    user.setFirstName(nameParts[0]);
                                    user.setLastName(nameParts.length > 1 ? nameParts[1] : "");
                                    // Set a temporary password - they'll need to reset it
                                    String tempPassword = generateTempPassword();
                                    user.setPassword(tempPassword);
                                    user.setRole("PC"); // Set as Program Committee member
                                    user.setInstitution("TBD");
                                    user.setCity("TBD");
                                    user.setCountry("TBD");
                                    user.setActive(true);
                                    
                                    if (!userDAO.create(user)) {
                                        throw new SQLException("Failed to create user account for committee member");
                                    }

                                    // Send email notification to new committee member
                                    try {
                                        EmailUtil.sendCommitteeMemberNotification(
                                            email,
                                            fullName,
                                            conference.getName(),
                                            committeeName,
                                            tempPassword
                                        );
                                    } catch (MessagingException e) {
                                        System.err.println("Failed to send committee member notification email: " + e.getMessage());
                                        e.printStackTrace();
                                        // Continue with the process even if email fails
                                    }
                                }

                                // At this point, user.getUserId() should not be null
                                if (user.getUserId() == null) {
                                    throw new SQLException("Failed to get user ID for committee member");
                                }

                                CommitteeMember member = new CommitteeMember();
                                member.setConferenceId(conference.getConferenceId());
                                member.setUserId(user.getUserId());
                                member.setEmail(email);
                                member.setName(fullName);
                                member.setCommitteeType("PC"); // Program Committee
                                member.setCommitteeName(committeeName);
                                member.setResponsible(false);
                                
                                if (!committeeDAO.create(member)) {
                                    throw new SQLException("Failed to create committee member");
                                }

                                // Send email notification to existing committee member
                                if (user != null && user.getUserId() != null) {
                                    try {
                                        EmailUtil.sendCommitteeMemberNotification(
                                            email,
                                            fullName,
                                            conference.getName(),
                                            committeeName,
                                            null // No temp password for existing users
                                        );
                                    } catch (MessagingException e) {
                                        System.err.println("Failed to send committee member notification email: " + e.getMessage());
                                        e.printStackTrace();
                                        // Continue with the process even if email fails
                                    }
                                }
                            }
                        }
                    }

                    // Commit transaction
                    conn.commit();
                    
                    session.setAttribute("successMessage", "Conference created successfully");
                    response.sendRedirect(request.getContextPath() + "/dashboard");
                } else {
                    conn.rollback();
                    request.setAttribute("error", "Failed to create conference");
                    request.getRequestDispatcher("/WEB-INF/views/conference/create.jsp").forward(request, response);
                }
            } catch (Exception e) {
                // Rollback transaction on error
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException se) {
                        se.printStackTrace();
                    }
                }
                System.err.println("Error creating conference: " + e.getMessage());
                e.printStackTrace();
                request.setAttribute("error", "An error occurred while creating the conference: " + e.getMessage());
                request.getRequestDispatcher("/WEB-INF/views/conference/create.jsp").forward(request, response);
            }
        } catch (Exception e) {
            System.err.println("Error processing request: " + e.getMessage());
            e.printStackTrace();
            request.setAttribute("error", "An error occurred while processing your request: " + e.getMessage());
            request.getRequestDispatcher("/WEB-INF/views/conference/create.jsp").forward(request, response);
        } finally {
            // Reset auto-commit and close connection
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private boolean isValidConferenceType(String type) {
        return type != null && (type.equals("Physical") || type.equals("Virtual") || type.equals("Hybrid"));
    }

    private String generateTempPassword() {
        return StringUtil.generateShortUUID();
    }
} 