package com.paximum.paxassist.auth.exception;

// Thrown when a self-service password reset targets an email that isn't registered. Unlike the
// old request-a-link flow (which hid account existence), a direct reset must tell the user whether
// it worked, so this maps to a 404 the client can surface. See AuthService#resetPassword.
public class EmailNotFoundException extends RuntimeException {

    public EmailNotFoundException(String email) {
        super("No account found for email '" + email + "'");
    }
}
