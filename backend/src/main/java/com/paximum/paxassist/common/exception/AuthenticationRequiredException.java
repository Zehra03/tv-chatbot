package com.paximum.paxassist.common.exception;

// Token eksik/geçersiz durumları için; "E-posta veya şifre hatalı" (InvalidCredentialsException)
// yalnızca login denemesinde kullanılır.
public class AuthenticationRequiredException extends RuntimeException {

    public AuthenticationRequiredException() {
        super("Geçerli bir oturum bulunamadı, lütfen giriş yapın");
    }
}
