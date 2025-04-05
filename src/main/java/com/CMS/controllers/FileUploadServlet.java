package com.campusconf.controllers;

import com.campusconf.models.Submission;
import com.campusconf.services.SubmissionService;
import com.campusconf.utils.ConstantsUtil;
import com.campusconf.utils.FileUploadUtil;
import com.campusconf.utils.JsonUtil;
import com.campusconf.utils.LogUtil;
import com.campusconf.utils.SecurityUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/upload/*")
@MultipartConfig(
    maxFileSize = 10485760, // 10MB
    maxRequestSize = 10485760,
    fileSizeThreshold = 5242880 // 5MB
)
public class FileUploadServlet extends HttpServlet {
    private final SubmissionService submissionService;

    public FileUploadServlet() {
        this.submissionService = new SubmissionService();
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
        if (pathInfo == null || pathInfo.equals("/")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid request path");
            return;
        }

        try {
            switch (pathInfo) {
                case "/submission":
                    handleSubmissionUpload(request, response, session);
                    break;
                case "/revision":
                    handleRevisionUpload(request, response, session);
                    break;
                default:
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid upload operation");
            }
        } catch (Exception e) {
            handleError(response, "Error processing file upload", e);
        }
    }

    private void handleSubmissionUpload(HttpServletRequest request, HttpServletResponse response, HttpSession session) 
            throws IOException, ServletException, SQLException {
        // Get submission ID from request
        String submissionId = request.getParameter("submissionId");
        if (submissionId == null || submissionId.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Submission ID is required");
            return;
        }

        // Get the file part
        Part filePart = request.getPart("document");
        if (filePart == null || filePart.getSize() == 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No file uploaded");
            return;
        }

        // Validate file
        String fileName = filePart.getSubmittedFileName();
        if (!SecurityUtil.isValidFileType(fileName)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, ConstantsUtil.ERROR_INVALID_FILE_TYPE);
            return;
        }

        if (!SecurityUtil.isValidFileSize(filePart.getSize())) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, ConstantsUtil.ERROR_FILE_TOO_LARGE);
            return;
        }

        // Save file
        String savedFileName = FileUploadUtil.saveFile(filePart);
        LogUtil.logFileUpload("Submission document uploaded", fileName, savedFileName);

        // Update submission with new document path
        Submission submission = submissionService.getSubmissionById(submissionId);
        if (submission == null) {
            FileUploadUtil.deleteFile(savedFileName);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Submission not found");
            return;
        }

        // Delete old file if exists
        if (submission.getDocumentPath() != null) {
            FileUploadUtil.deleteFile(submission.getDocumentPath());
        }

        submission.setDocumentPath(savedFileName);
        boolean updated = submissionService.updateSubmission(submission);

        if (updated) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", ConstantsUtil.SUCCESS_SUBMISSION);
            result.put("fileName", savedFileName);
            
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");
            response.getWriter().write(JsonUtil.toJsonObject(result));
        } else {
            FileUploadUtil.deleteFile(savedFileName);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update submission");
        }
    }

    private void handleRevisionUpload(HttpServletRequest request, HttpServletResponse response, HttpSession session) 
            throws IOException, ServletException, SQLException {
        // Get submission ID from request
        String submissionId = request.getParameter("submissionId");
        if (submissionId == null || submissionId.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Submission ID is required");
            return;
        }

        // Get the file part
        Part filePart = request.getPart("document");
        if (filePart == null || filePart.getSize() == 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No file uploaded");
            return;
        }

        // Validate file
        String fileName = filePart.getSubmittedFileName();
        if (!SecurityUtil.isValidFileType(fileName)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, ConstantsUtil.ERROR_INVALID_FILE_TYPE);
            return;
        }

        if (!SecurityUtil.isValidFileSize(filePart.getSize())) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, ConstantsUtil.ERROR_FILE_TOO_LARGE);
            return;
        }

        // Save file
        String savedFileName = FileUploadUtil.saveFile(filePart);
        LogUtil.logFileUpload("Revision document uploaded", fileName, savedFileName);

        // Update submission with new document path
        Submission submission = submissionService.getSubmissionById(submissionId);
        if (submission == null) {
            FileUploadUtil.deleteFile(savedFileName);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Submission not found");
            return;
        }

        // Delete old file if exists
        if (submission.getDocumentPath() != null) {
            FileUploadUtil.deleteFile(submission.getDocumentPath());
        }

        submission.setDocumentPath(savedFileName);
        submission.setStatus("REVISION_SUBMITTED");
        boolean updated = submissionService.updateSubmission(submission);

        if (updated) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "Revision submitted successfully");
            result.put("fileName", savedFileName);
            
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");
            response.getWriter().write(JsonUtil.toJsonObject(result));
        } else {
            FileUploadUtil.deleteFile(savedFileName);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update submission");
        }
    }

    private void handleError(HttpServletResponse response, String message, Exception e) 
            throws IOException {
        LogUtil.logFileUploadError(e.getMessage(), message);
        
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("details", e.getMessage());
        
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setContentType("application/json");
        response.getWriter().write(JsonUtil.toJsonObject(error));
    }
} 