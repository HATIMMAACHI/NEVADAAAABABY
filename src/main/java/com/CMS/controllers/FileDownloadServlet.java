package com.campusconf.controllers;

import com.campusconf.models.Submission;
import com.campusconf.services.SubmissionService;
import com.campusconf.utils.ConstantsUtil;
import com.campusconf.utils.FileUploadUtil;
import com.campusconf.utils.LogUtil;
import com.campusconf.utils.SecurityUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;

@WebServlet("/download/*")
public class FileDownloadServlet extends HttpServlet {
    private final SubmissionService submissionService;

    public FileDownloadServlet() {
        this.submissionService = new SubmissionService();
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
                case "/submission":
                    handleSubmissionDownload(request, response, session);
                    break;
                case "/revision":
                    handleRevisionDownload(request, response, session);
                    break;
                default:
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid download operation");
            }
        } catch (Exception e) {
            handleError(response, "Error processing file download", e);
        }
    }

    private void handleSubmissionDownload(HttpServletRequest request, HttpServletResponse response, HttpSession session) 
            throws IOException, SQLException {
        // Get submission ID from request
        String submissionId = request.getParameter("submissionId");
        if (submissionId == null || submissionId.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Submission ID is required");
            return;
        }

        // Get submission details
        Submission submission = submissionService.getSubmissionById(submissionId);
        if (submission == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Submission not found");
            return;
        }

        // Check if user has permission to download
        String userRole = (String) session.getAttribute("role");
        Long userId = (Long) session.getAttribute("userId");
        
        boolean hasPermission = checkDownloadPermission(submission, userRole, userId);
        if (!hasPermission) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "You don't have permission to download this file");
            return;
        }

        // Get file and send it
        String documentPath = submission.getDocumentPath();
        if (documentPath == null || documentPath.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Document not found");
            return;
        }

        File file = FileUploadUtil.getFile(documentPath);
        if (!file.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Document file not found");
            return;
        }

        // Set response headers
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
        response.setHeader("Content-Length", String.valueOf(file.length()));

        // Send file
        try (FileInputStream fileInputStream = new FileInputStream(file);
             OutputStream outputStream = response.getOutputStream()) {
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }

        LogUtil.logFileDownload("Submission document downloaded", file.getName(), submissionId);
    }

    private void handleRevisionDownload(HttpServletRequest request, HttpServletResponse response, HttpSession session) 
            throws IOException, SQLException {
        // Get submission ID from request
        String submissionId = request.getParameter("submissionId");
        if (submissionId == null || submissionId.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Submission ID is required");
            return;
        }

        // Get submission details
        Submission submission = submissionService.getSubmissionById(submissionId);
        if (submission == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Submission not found");
            return;
        }

        // Check if user has permission to download
        String userRole = (String) session.getAttribute("role");
        Long userId = (Long) session.getAttribute("userId");
        
        boolean hasPermission = checkDownloadPermission(submission, userRole, userId);
        if (!hasPermission) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "You don't have permission to download this file");
            return;
        }

        // Get file and send it
        String documentPath = submission.getDocumentPath();
        if (documentPath == null || documentPath.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Document not found");
            return;
        }

        File file = FileUploadUtil.getFile(documentPath);
        if (!file.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Document file not found");
            return;
        }

        // Set response headers
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
        response.setHeader("Content-Length", String.valueOf(file.length()));

        // Send file
        try (FileInputStream fileInputStream = new FileInputStream(file);
             OutputStream outputStream = response.getOutputStream()) {
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }

        LogUtil.logFileDownload("Revision document downloaded", file.getName(), submissionId);
    }

    private boolean checkDownloadPermission(Submission submission, String userRole, Long userId) {
        // Authors can download their own submissions
        if (submission.getCorrespondingAuthorId().equals(userId)) {
            return true;
        }

        // Committee members can download submissions they are reviewing
        switch (userRole) {
            case "PC Member":
            case "SC Member":
            case "SC Resp":
                return true;
            default:
                return false;
        }
    }

    private void handleError(HttpServletResponse response, String message, Exception e) 
            throws IOException {
        LogUtil.logFileDownloadError(e.getMessage(), message);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message);
    }
} 