package com.campusconf.controllers;

import com.campusconf.models.Notification;
import com.campusconf.services.NotificationService;
import com.campusconf.utils.ConstantsUtil;
import com.campusconf.utils.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/notifications/*")
public class NotificationServlet extends HttpServlet {
    private final NotificationService notificationService;

    public NotificationServlet() {
        this.notificationService = new NotificationService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        Long userId = (Long) session.getAttribute("userId");
        String pathInfo = request.getPathInfo();

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                // Get all notifications for the user
                List<Notification> notifications = notificationService.getNotificationsByUserId(userId);
                request.setAttribute("notifications", notifications);
                request.getRequestDispatcher("/WEB-INF/views/notifications/list.jsp").forward(request, response);
            } else if (pathInfo.equals("/count")) {
                // Get unread notification count
                int count = notificationService.countUnreadNotifications(userId);
                response.setContentType("application/json");
                response.getWriter().write(JsonUtil.toJsonObject(Map.of("count", count)));
            } else if (pathInfo.equals("/unread")) {
                // Get unread notifications
                List<Notification> unreadNotifications = notificationService.getNotificationsByUserIdAndStatus(userId, ConstantsUtil.NOTIFICATION_UNREAD);
                response.setContentType("application/json");
                response.getWriter().write(JsonUtil.toJsonArray(unreadNotifications));
            }
        } catch (SQLException e) {
            handleError(response, "Error retrieving notifications", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not authenticated");
            return;
        }

        String pathInfo = request.getPathInfo();
        Long userId = (Long) session.getAttribute("userId");

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                // Create new notification
                String title = request.getParameter("title");
                String message = request.getParameter("message");
                String type = request.getParameter("type");

                if (title == null || message == null || type == null) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing required fields");
                    return;
                }

                Notification notification = new Notification();
                notification.setUserId(userId);
                notification.setTitle(title);
                notification.setMessage(message);
                notification.setType(type);
                notification.setStatus(ConstantsUtil.NOTIFICATION_UNREAD);

                boolean created = notificationService.createNotification(notification);
                if (created) {
                    response.setStatus(HttpServletResponse.SC_CREATED);
                    Map<String, Object> notificationMap = new HashMap<>();
                    notificationMap.put("id", notification.getId());
                    notificationMap.put("userId", notification.getUserId());
                    notificationMap.put("title", notification.getTitle());
                    notificationMap.put("message", notification.getMessage());
                    notificationMap.put("type", notification.getType());
                    notificationMap.put("status", notification.getStatus());
                    notificationMap.put("createdAt", notification.getCreatedAt());
                    response.getWriter().write(JsonUtil.toJsonObject(notificationMap));
                } else {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to create notification");
                }
            } else if (pathInfo.equals("/mark-read")) {
                // Mark notification as read
                String notificationId = request.getParameter("notificationId");
                if (notificationId == null) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Notification ID is required");
                    return;
                }

                boolean marked = notificationService.markAsRead(Long.parseLong(notificationId));
                if (marked) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.getWriter().write(JsonUtil.toJsonObject(Map.of("status", "success")));
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Notification not found");
                }
            } else if (pathInfo.equals("/mark-all-read")) {
                // Mark all notifications as read
                boolean marked = notificationService.markAllAsRead(userId);
                if (marked) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.getWriter().write(JsonUtil.toJsonObject(Map.of("status", "success")));
                } else {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to mark notifications as read");
                }
            }
        } catch (SQLException e) {
            handleError(response, "Error processing notification request", e);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not authenticated");
            return;
        }

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Notification ID is required");
            return;
        }

        try {
            Long notificationId = Long.parseLong(pathInfo.substring(1));
            boolean deleted = notificationService.deleteNotification(notificationId);
            
            if (deleted) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write(JsonUtil.toJsonObject(Map.of("status", "success")));
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Notification not found");
            }
        } catch (SQLException e) {
            handleError(response, "Error deleting notification", e);
        }
    }

    private void handleError(HttpServletResponse response, String message, Exception e) 
            throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("details", e.getMessage());
        
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setContentType("application/json");
        response.getWriter().write(JsonUtil.toJsonObject(error));
    }
} 