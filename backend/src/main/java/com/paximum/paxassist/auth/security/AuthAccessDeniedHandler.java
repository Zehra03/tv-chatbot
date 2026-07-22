package com.paximum.paxassist.auth.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paximum.paxassist.chat.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles an authenticated-but-unauthorized request (role check failed): returns a standard 403
 * body and logs the event asynchronously.
 */
@Component
public class AuthAccessDeniedHandler implements AccessDeniedHandler {

    private final AuthAuditLogger auditLogger;
    private final ObjectMapper objectMapper;

    public AuthAccessDeniedHandler(AuthAuditLogger auditLogger, ObjectMapper objectMapper) {
        this.auditLogger = auditLogger;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        String principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "anonymous";
        auditLogger.logAccessDenied(request.getRequestURI(), principal);

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                new ErrorResponse("ACCESS_DENIED", "You do not have permission to access this resource")));
    }
}
