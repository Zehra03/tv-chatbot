package com.paximum.paxassist.auth.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paximum.paxassist.chat.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles unauthenticated access to a protected endpoint (missing/invalid bearer token): returns
 * a standard 401 body and logs the event asynchronously instead of leaking a container error page.
 */
@Component
public class AuthEntryPoint implements AuthenticationEntryPoint {

    private final AuthAuditLogger auditLogger;
    private final ObjectMapper objectMapper;

    public AuthEntryPoint(AuthAuditLogger auditLogger, ObjectMapper objectMapper) {
        this.auditLogger = auditLogger;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        auditLogger.logAuthenticationFailureAsync(request.getRequestURI(), authException.getMessage());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                new ErrorResponse("UNAUTHENTICATED", "Authentication is required to access this resource")));
    }
}
