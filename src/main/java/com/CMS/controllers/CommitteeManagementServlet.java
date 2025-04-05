package com.campusconf.controllers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.campusconf.dao.ConferenceDAO;
import com.campusconf.dao.impl.ConferenceDAOImpl;
import com.campusconf.dao.CommitteeMemberDAO;
import com.campusconf.dao.impl.CommitteeMemberDAOImpl;
import com.campusconf.models.Conference;
import com.campusconf.models.CommitteeMember;
import com.campusconf.utils.ValidationUtils;
import com.campusconf.utils.DatabaseUtil;

@WebServlet("/conference/committee/*")
public class CommitteeManagementServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private ConferenceDAO conferenceDAO;
    private CommitteeMemberDAO committeeDAO;

    @Override
    public void init() throws ServletException {
        try {
            conferenceDAO = new ConferenceDAOImpl();
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

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        try {
            Long conferenceId = Long.parseLong(pathInfo.substring(1));
            Conference conference = conferenceDAO.findById(conferenceId);
            
            if (conference == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // Check if user is the president of the conference
            Long userId = (Long) session.getAttribute("userId");
            if (!conference.getPresidentId().equals(userId)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            // Get committee members
            List<CommitteeMember> pcMembers = committeeDAO.findByCommitteeType(conferenceId, "PC");
            List<CommitteeMember> scMembers = committeeDAO.findByCommitteeType(conferenceId, "SC");

            request.setAttribute("conference", conference);
            request.setAttribute("pcMembers", pcMembers);
            request.setAttribute("scMembers", scMembers);
            request.getRequestDispatcher("/WEB-INF/views/conference/committee.jsp").forward(request, response);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        } catch (SQLException e) {
            request.setAttribute("error", "Database error occurred while fetching committee members.");
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

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        try {
            Long conferenceId = Long.parseLong(pathInfo.substring(1));
            Conference conference = conferenceDAO.findById(conferenceId);
            
            if (conference == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // Check if user is the president of the conference
            Long userId = (Long) session.getAttribute("userId");
            if (!conference.getPresidentId().equals(userId)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            String action = request.getParameter("action");
            if (action == null || action.trim().isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            Map<String, String> errors = new HashMap<>();
            
            switch (action) {
                case "add":
                    addCommitteeMember(request, response, conferenceId, errors);
                    break;
                case "update":
                    updateCommitteeMember(request, response, conferenceId, errors);
                    break;
                case "delete":
                    deleteCommitteeMember(request, response, conferenceId, errors);
                    break;
                case "setResp":
                    setSCResponsible(request, response, conferenceId, errors);
                    break;
                default:
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            }
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        } catch (SQLException e) {
            request.setAttribute("error", "Database error occurred while managing committee members.");
            request.getRequestDispatcher("/WEB-INF/views/error.jsp").forward(request, response);
        }
    }

    private void addCommitteeMember(HttpServletRequest request, HttpServletResponse response, 
            Long conferenceId, Map<String, String> errors) throws SQLException, ServletException, IOException {
        String email = request.getParameter("email");
        String committeeType = request.getParameter("committeeType");
        String academicTitle = request.getParameter("academicTitle");
        String expertiseAreas = request.getParameter("expertiseAreas");
        String biography = request.getParameter("biography");

        // Validate input
        if (email == null || !ValidationUtils.isValidEmail(email)) {
            errors.put("email", "Invalid email address");
        }
        if (committeeType == null || !isValidCommitteeType(committeeType)) {
            errors.put("committeeType", "Invalid committee type");
        }
        if (academicTitle == null || academicTitle.trim().isEmpty()) {
            errors.put("academicTitle", "Academic title is required");
        }
        if (expertiseAreas == null || expertiseAreas.trim().isEmpty()) {
            errors.put("expertiseAreas", "Areas of expertise are required");
        }

        if (!errors.isEmpty()) {
            request.setAttribute("errors", errors);
            response.sendRedirect(request.getContextPath() + "/conference/committee/" + conferenceId);
            return;
        }

        CommitteeMember member = new CommitteeMember();
        member.setConferenceId(conferenceId);
        member.setCommitteeType(committeeType);
        member.setAcademicTitle(academicTitle);
        member.setExpertiseAreas(expertiseAreas);
        member.setBiography(biography);
        member.setResponsible(false);

        if (committeeDAO.create(member)) {
            request.getSession().setAttribute("successMessage", "Committee member added successfully");
        } else {
            request.setAttribute("error", "Failed to add committee member");
            request.getRequestDispatcher("/WEB-INF/views/error.jsp").forward(request, response);
            return;
        }

        response.sendRedirect(request.getContextPath() + "/conference/committee/" + conferenceId);
    }

    private void updateCommitteeMember(HttpServletRequest request, HttpServletResponse response, 
            Long conferenceId, Map<String, String> errors) throws SQLException, ServletException, IOException {
        Long memberId = Long.parseLong(request.getParameter("memberId"));
        String academicTitle = request.getParameter("academicTitle");
        String expertiseAreas = request.getParameter("expertiseAreas");
        String biography = request.getParameter("biography");

        // Validate input
        if (academicTitle == null || academicTitle.trim().isEmpty()) {
            errors.put("academicTitle", "Academic title is required");
        }
        if (expertiseAreas == null || expertiseAreas.trim().isEmpty()) {
            errors.put("expertiseAreas", "Areas of expertise are required");
        }

        if (!errors.isEmpty()) {
            request.setAttribute("errors", errors);
            response.sendRedirect(request.getContextPath() + "/conference/committee/" + conferenceId);
            return;
        }

        CommitteeMember member = committeeDAO.findById(memberId);
        if (member == null || !member.getConferenceId().equals(conferenceId)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        member.setAcademicTitle(academicTitle);
        member.setExpertiseAreas(expertiseAreas);
        member.setBiography(biography);

        if (committeeDAO.update(member)) {
            request.getSession().setAttribute("successMessage", "Committee member updated successfully");
        } else {
            request.setAttribute("error", "Failed to update committee member");
            request.getRequestDispatcher("/WEB-INF/views/error.jsp").forward(request, response);
            return;
        }

        response.sendRedirect(request.getContextPath() + "/conference/committee/" + conferenceId);
    }

    private void deleteCommitteeMember(HttpServletRequest request, HttpServletResponse response, 
            Long conferenceId, Map<String, String> errors) throws SQLException, ServletException, IOException {
        Long memberId = Long.parseLong(request.getParameter("memberId"));
        String committeeType = request.getParameter("committeeType");

        CommitteeMember member = committeeDAO.findById(memberId);
        if (member == null || !member.getConferenceId().equals(conferenceId)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // For SC members, check if they are responsible
        if ("SC".equals(committeeType) && member.isResponsible()) {
            errors.put("delete", "Cannot delete the responsible SC member. Please assign a new responsible member first.");
            request.setAttribute("errors", errors);
            response.sendRedirect(request.getContextPath() + "/conference/committee/" + conferenceId);
            return;
        }

        if (committeeDAO.delete(memberId)) {
            request.getSession().setAttribute("successMessage", "Committee member deleted successfully");
        } else {
            request.setAttribute("error", "Failed to delete committee member");
            request.getRequestDispatcher("/WEB-INF/views/error.jsp").forward(request, response);
            return;
        }

        response.sendRedirect(request.getContextPath() + "/conference/committee/" + conferenceId);
    }

    private void setSCResponsible(HttpServletRequest request, HttpServletResponse response, 
            Long conferenceId, Map<String, String> errors) throws SQLException, ServletException, IOException {
        Long memberId = Long.parseLong(request.getParameter("memberId"));

        CommitteeMember member = committeeDAO.findById(memberId);
        if (member == null || !member.getConferenceId().equals(conferenceId) || !"SC".equals(member.getCommitteeType())) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (committeeDAO.updateResponsibleMember(conferenceId, "SC", memberId)) {
            request.getSession().setAttribute("successMessage", "SC responsible member updated successfully");
        } else {
            request.setAttribute("error", "Failed to update SC responsible member");
            request.getRequestDispatcher("/WEB-INF/views/error.jsp").forward(request, response);
            return;
        }

        response.sendRedirect(request.getContextPath() + "/conference/committee/" + conferenceId);
    }

    private boolean isValidCommitteeType(String type) {
        return type != null && (type.equals("PC") || type.equals("SC"));
    }
} 