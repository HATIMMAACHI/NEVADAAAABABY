package com.campusconf.controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.campusconf.models.Conference;
import com.campusconf.models.User;
import com.campusconf.services.ConferenceService;
import com.campusconf.utils.DatabaseUtil;
import com.campusconf.utils.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/conference/*")
public class ConferenceServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private ConferenceService conferenceService;

    @Override
    public void init() throws ServletException {
        try {
            Connection connection = DatabaseUtil.getConnection();
            conferenceService = new ConferenceService(connection);
        } catch (SQLException e) {
            throw new ServletException("Failed to initialize ConferenceServlet", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        try {
            String pathInfo = request.getPathInfo();
            
            if (pathInfo == null || pathInfo.equals("/")) {
                // Get all conferences
                List<Conference> conferences = conferenceService.getAllConferences();
                JsonUtil.sendJsonResponse(response, conferences);
            } else {
                // Get conference by ID
                String[] pathParts = pathInfo.split("/");
                if (pathParts.length == 2) {
                    Long conferenceId = Long.parseLong(pathParts[1]);
                    Conference conference = conferenceService.getConferenceById(conferenceId);
                    if (conference != null) {
                        JsonUtil.sendJsonResponse(response, conference);
                    } else {
                        JsonUtil.sendErrorResponse(response, "Conference not found", 404);
                    }
                } else {
                    JsonUtil.sendErrorResponse(response, "Invalid URL format", 400);
                }
            }
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(response, "Database error: " + e.getMessage(), 500);
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(response, "Invalid conference ID format", 400);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        try {
            // Check user authentication
            HttpSession session = request.getSession(false);
            if (session == null || session.getAttribute("user") == null) {
                JsonUtil.sendErrorResponse(response, "User not authenticated", 401);
                return;
            }

            // Parse conference data from request body
            String requestBody = request.getReader().lines()
                    .reduce("", (accumulator, actual) -> accumulator + actual);
            Conference conference = JsonUtil.fromJson(requestBody, Conference.class);

            // Set the president ID to the current user
            User currentUser = (User) session.getAttribute("user");
            conference.setPresidentId(currentUser.getUserId());

            // Create the conference
            Conference createdConference = conferenceService.createConference(conference);
            JsonUtil.sendJsonResponse(response, createdConference, 201);
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(response, "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response, "Error creating conference: " + e.getMessage(), 400);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        try {
            // Check user authentication
            HttpSession session = request.getSession(false);
            if (session == null || session.getAttribute("user") == null) {
                JsonUtil.sendErrorResponse(response, "User not authenticated", 401);
                return;
            }

            // Get conference ID from URL
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.equals("/")) {
                JsonUtil.sendErrorResponse(response, "Conference ID is required", 400);
                return;
            }

            String[] pathParts = pathInfo.split("/");
            if (pathParts.length != 2) {
                JsonUtil.sendErrorResponse(response, "Invalid URL format", 400);
                return;
            }

            Long conferenceId = Long.parseLong(pathParts[1]);
            User currentUser = (User) session.getAttribute("user");

            // Check if user is the conference president
            Conference existingConference = conferenceService.getConferenceById(conferenceId);
            if (existingConference == null) {
                JsonUtil.sendErrorResponse(response, "Conference not found", 404);
                return;
            }

            if (!existingConference.getPresidentId().equals(currentUser.getUserId())) {
                JsonUtil.sendErrorResponse(response, "Unauthorized to update this conference", 403);
                return;
            }

            // Parse updated conference data
            String requestBody = request.getReader().lines()
                    .reduce("", (accumulator, actual) -> accumulator + actual);
            Conference updatedConference = JsonUtil.fromJson(requestBody, Conference.class);
            updatedConference.setConferenceId(conferenceId);
            updatedConference.setPresidentId(currentUser.getUserId());

            // Update the conference
            Conference result = conferenceService.updateConference(updatedConference);
            JsonUtil.sendJsonResponse(response, result);
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(response, "Database error: " + e.getMessage(), 500);
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(response, "Invalid conference ID format", 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response, "Error updating conference: " + e.getMessage(), 400);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        try {
            // Check user authentication
            HttpSession session = request.getSession(false);
            if (session == null || session.getAttribute("user") == null) {
                JsonUtil.sendErrorResponse(response, "User not authenticated", 401);
                return;
            }

            // Get conference ID from URL
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.equals("/")) {
                JsonUtil.sendErrorResponse(response, "Conference ID is required", 400);
                return;
            }

            String[] pathParts = pathInfo.split("/");
            if (pathParts.length != 2) {
                JsonUtil.sendErrorResponse(response, "Invalid URL format", 400);
                return;
            }

            Long conferenceId = Long.parseLong(pathParts[1]);
            User currentUser = (User) session.getAttribute("user");

            // Check if user is the conference president
            Conference conference = conferenceService.getConferenceById(conferenceId);
            if (conference == null) {
                JsonUtil.sendErrorResponse(response, "Conference not found", 404);
                return;
            }

            if (!conference.getPresidentId().equals(currentUser.getUserId())) {
                JsonUtil.sendErrorResponse(response, "Unauthorized to delete this conference", 403);
                return;
            }

            // Delete the conference
            boolean deleted = conferenceService.deleteConference(conferenceId);
            if (deleted) {
                JsonUtil.sendSuccessResponse(response, "Conference deleted successfully");
            } else {
                JsonUtil.sendErrorResponse(response, "Failed to delete conference", 500);
            }
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(response, "Database error: " + e.getMessage(), 500);
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(response, "Invalid conference ID format", 400);
        }
    }
}