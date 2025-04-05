<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Conference Management System</title>

    <!-- Tailwind CSS -->
    <script src="https://cdn.tailwindcss.com"></script>

    <!-- Google Fonts -->
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">

    <!-- Font Awesome -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0-beta3/css/all.min.css">

    <style>
        body {
            font-family: 'Inter', sans-serif;
        }
    </style>
</head>
<body class="bg-gray-50">

<!-- Navigation -->
<nav class="bg-white shadow-lg">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between h-16">
            <div class="flex items-center">
                <i class="fas fa-university text-purple-600 text-2xl mr-2"></i>
                <span class="text-xl font-semibold text-gray-800">ConferenceMS</span>
            </div>
            <div class="flex items-center">
                <c:choose>
                    <c:when test="${empty sessionScope.user}">
                        <a href="USER.jsp" class="text-gray-600 hover:text-gray-900 px-3 py-2 rounded-md text-sm font-medium">
                            Login
                        </a>
                        <a href="USER.jsp" class="ml-4 bg-purple-600 hover:bg-purple-700 text-white px-4 py-2 rounded-md text-sm font-medium">
                            Register
                        </a>
                    </c:when>
                    <c:otherwise>
                        <a href="${pageContext.request.contextPath}/user/profile" class="text-gray-600 hover:text-gray-900 px-3 py-2 rounded-md text-sm font-medium">
                            <i class="fas fa-user mr-2"></i>${sessionScope.user.firstName}
                        </a>
                        <a href="${pageContext.request.contextPath}/user/logout" class="ml-4 text-red-600 hover:text-red-700 px-3 py-2 rounded-md text-sm font-medium">
                            <i class="fas fa-sign-out-alt mr-2"></i>Logout
                        </a>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>
    </div>
</nav>

<!-- Hero Section -->
<div class="bg-gradient-to-r from-purple-600 to-purple-800 text-white py-20">
    <div class="max-w-4xl mx-auto px-4">
        <h1 class="text-5xl font-extrabold leading-tight mb-6">
            Manage Your <br><span class="text-yellow-300">Academic Conferences</span>
        </h1>
        <p class="text-xl mb-6">
            Streamline your conference management process with our comprehensive platform.
            From paper submissions to review management, we've got you covered.
        </p>
        <div class="flex flex-col sm:flex-row gap-4">
            <a href="${pageContext.request.contextPath}/conference/list" class="bg-white text-purple-700 font-semibold px-6 py-3 rounded-md shadow hover:bg-gray-100 text-lg">
                Browse Conferences
            </a>
            <a href="${pageContext.request.contextPath}/paper/submit" class="bg-yellow-400 text-purple-900 font-semibold px-6 py-3 rounded-md shadow hover:bg-yellow-300 text-lg">
                Submit Paper
            </a>
        </div>
    </div>
</div>

<!-- Features Section -->
<div class="py-16 bg-gray-50">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="lg:text-center">
            <h2 class="text-base text-purple-600 font-semibold tracking-wide uppercase">Features</h2>
            <p class="mt-2 text-3xl leading-8 font-extrabold tracking-tight text-gray-900 sm:text-4xl">
                Everything you need to manage your conference
            </p>
        </div>

        <div class="mt-10 grid grid-cols-1 md:grid-cols-2 gap-8">
            <!-- Feature 1 -->
            <div class="relative pl-16">
                <div class="absolute left-0 top-0 flex items-center justify-center h-12 w-12 rounded-md bg-purple-500 text-white">
                    <i class="fas fa-paper-plane text-xl"></i>
                </div>
                <h3 class="text-lg font-medium text-gray-900">Paper Submissions</h3>
                <p class="mt-2 text-base text-gray-600">Easy submission process for authors with support for multiple file formats.</p>
            </div>

            <!-- Feature 2 -->
            <div class="relative pl-16">
                <div class="absolute left-0 top-0 flex items-center justify-center h-12 w-12 rounded-md bg-purple-500 text-white">
                    <i class="fas fa-users text-xl"></i>
                </div>
                <h3 class="text-lg font-medium text-gray-900">Committee Management</h3>
                <p class="mt-2 text-base text-gray-600">Efficient management of scientific and program committees.</p>
            </div>

            <!-- Feature 3 -->
            <div class="relative pl-16">
                <div class="absolute left-0 top-0 flex items-center justify-center h-12 w-12 rounded-md bg-purple-500 text-white">
                    <i class="fas fa-check-double text-xl"></i>
                </div>
                <h3 class="text-lg font-medium text-gray-900">Review Process</h3>
                <p class="mt-2 text-base text-gray-600">Streamlined review process with automatic notifications and deadlines.</p>
            </div>

            <!-- Feature 4 -->
            <div class="relative pl-16">
                <div class="absolute left-0 top-0 flex items-center justify-center h-12 w-12 rounded-md bg-purple-500 text-white">
                    <i class="fas fa-chart-bar text-xl"></i>
                </div>
                <h3 class="text-lg font-medium text-gray-900">Statistics & Reports</h3>
                <p class="mt-2 text-base text-gray-600">Comprehensive statistics and reporting tools for conference management.</p>
            </div>
        </div>
    </div>
</div>

<!-- Footer -->
<footer class="bg-white">
    <div class="max-w-7xl mx-auto py-12 px-4 sm:px-6 md:flex md:items-center md:justify-between lg:px-8">
        <div class="flex justify-center space-x-6 md:order-2">
            <a href="#" class="text-gray-400 hover:text-purple-600">
                <i class="fab fa-twitter"></i>
            </a>
            <a href="#" class="text-gray-400 hover:text-purple-600">
                <i class="fab fa-facebook"></i>
            </a>
            <a href="#" class="text-gray-400 hover:text-purple-600">
                <i class="fab fa-linkedin"></i>
            </a>
        </div>
        <div class="mt-8 md:mt-0 md:order-1">
            <p class="text-center text-base text-gray-400">
                &copy; 2023 Conference Management System. All rights reserved.
            </p>
        </div>
    </div>
</footer>

</body>
</html>
