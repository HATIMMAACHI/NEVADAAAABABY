package com.campusconf.controllers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.sql.Date;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.campusconf.dao.ConferenceDAO;
import com.campusconf.dao.impl.ConferenceDAOImpl;
import com.campusconf.dao.ConferenceTopicDAO;
import com.campusconf.dao.impl.ConferenceTopicDAOImpl;
import com.campusconf.dao.CommitteeMemberDAO;
import com.campusconf.dao.impl.CommitteeMemberDAOImpl;
import com.campusconf.models.Conference;
import com.campusconf.models.ConferenceTopic;
import com.campusconf.models.CommitteeMember;
import com.campusconf.utils.ValidationUtils;
import com.campusconf.utils.JsonUtil;

@WebServlet("/conference/modify/*")
public class ConferenceModificationServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private ConferenceDAO conferenceDAO;
    private ConferenceTopicDAO topicDAO;
    private CommitteeMemberDAO committeeMemberDAO;

    @Override
    public void init() throws ServletException {
        try {
            conferenceDAO = new ConferenceDAOImpl();
            topicDAO = new ConferenceTopicDAOImpl();
            committeeMemberDAO = new CommitteeMemberDAOImpl();
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

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Long conferenceId = Long.parseLong(pathInfo.substring(1));
        Long userId = (Long) session.getAttribute("userId");

        try {
            Conference conference = conferenceDAO.findById(conferenceId);
            if (conference == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // Check if user is the conference president
            if (!conference.getPresidentId().equals(userId)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            // Get committee members
            List<CommitteeMember> committeeMembers = committeeMemberDAO.findByConferenceId(conferenceId);
            conference.setCommitteeMembers(committeeMembers);

            // Get topics and subtopics
            List<ConferenceTopic> topics = topicDAO.findByConferenceId(conferenceId);
            conference.setTopics(topics);

            request.setAttribute("conference", conference);
            request.getRequestDispatcher("/WEB-INF/views/conference/modify.jsp").forward(request, response);
        } catch (SQLException e) {
            throw new ServletException("Database error", e);
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

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Long conferenceId = Long.parseLong(pathInfo.substring(1));
        Long userId = (Long) session.getAttribute("userId");
        Map<String, String> errors = new HashMap<>();

        try {
            Conference conference = conferenceDAO.findById(conferenceId);
            if (conference == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // Check if user is the conference president
            if (!conference.getPresidentId().equals(userId)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

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
            String extensionDate = request.getParameter("extensionDate");
            String[] topics = request.getParameterValues("topics");
            String[] subtopics = request.getParameterValues("subtopics");

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
            if (extensionDate != null && !extensionDate.trim().isEmpty()) {
                Date extDate = Date.valueOf(extensionDate);
                Date subDeadline = Date.valueOf(submissionDeadline);
                if (extDate.before(subDeadline)) {
                    errors.put("extensionDate", "Extension date must be after submission deadline");
                }
            }

            if (!errors.isEmpty()) {
                response.setContentType("application/json");
                response.getWriter().write(JsonUtil.toJson(errors));
                return;
            }

            // Update conference
            conference.setName(name);
            conference.setAcronym(acronym);
            conference.setTheme(theme);
            conference.setType(type);
            conference.setWebsite(website);
            conference.setStartDate(Date.valueOf(startDate));
            conference.setEndDate(Date.valueOf(endDate));
            conference.setLocation(location);
            conference.setSubmissionDeadline(Date.valueOf(submissionDeadline));
            if (extensionDate != null && !extensionDate.trim().isEmpty()) {
                conference.setExtensionDate(Date.valueOf(extensionDate));
            }

            if (conferenceDAO.update(conference)) {
                // Update topics
                topicDAO.deleteTopicsByConferenceId(conferenceId); // Remove old topics
                if (topics != null) {
                    for (int i = 0; i < topics.length; i++) {
                        // Create main topic
                        ConferenceTopic topic = new ConferenceTopic();
                        topic.setConferenceId(conferenceId);
                        topic.setTopicName(topics[i]);
                        topicDAO.create(topic);

                        // Create subtopic if exists
                        if (subtopics != null && i < subtopics.length && subtopics[i] != null && !subtopics[i].trim().isEmpty()) {
                            ConferenceTopic subtopic = new ConferenceTopic();
                            subtopic.setConferenceId(conferenceId);
                            subtopic.setTopicName(subtopics[i]);
                            subtopic.setParentTopicId(topic.getTopicId());
                            topicDAO.create(subtopic);
                        }
                    }
                }

                Map<String, Object> success = new HashMap<>();
                success.put("success", true);
                success.put("message", "Conference updated successfully");
                response.setContentType("application/json");
                response.getWriter().write(JsonUtil.toJson(success));
            } else {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "Failed to update conference");
                response.setContentType("application/json");
                response.getWriter().write(JsonUtil.toJson(error));
            }
        } catch (SQLException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Database error occurred");
            response.setContentType("application/json");
            response.getWriter().write(JsonUtil.toJson(error));
        }
    }

    private boolean isValidConferenceType(String type) {
        return type != null && (type.equals("Physical") || type.equals("Virtual") || type.equals("Hybrid"));
    }
} 