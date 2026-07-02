package com.paximum.paxassist.auth.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("E-posta veya şifre hatalı");
    }
}
