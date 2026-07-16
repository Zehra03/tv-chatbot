package com.paximum.paxassist.auth.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.paximum.paxassist.auth.security.AuthAccessDeniedHandler;
import com.paximum.paxassist.auth.security.AuthEntryPoint;
import com.paximum.paxassist.auth.security.JwtAuthenticationFilter;
import com.paximum.paxassist.ratelimiter.RateLimitFilter;

import jakarta.servlet.DispatcherType;

/**
 * Central security filter chain for every endpoint in the monolith. Sits at the entry of the
 * ratelimiter -&gt; guard -&gt; orchestrator pipeline: stateless (JWT bearer only, no sessions),
 * CORS-restricted to the configured frontend origin, and path-based RBAC so business modules
 * (flight/hotel/reservation/chat) don't need their own security wiring.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthEntryPoint authEntryPoint;
    private final AuthAccessDeniedHandler accessDeniedHandler;
    private final String allowedOrigin;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthEntryPoint authEntryPoint,
            AuthAccessDeniedHandler accessDeniedHandler,
            @Value("${app.cors.allowed-origin}") String allowedOrigin) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authEntryPoint = authEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.allowedOrigin = allowedOrigin;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RateLimitFilter rateLimitFilter)
            throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // Spring Boot'un /error yönlendirmesi (ERROR dispatch) güvenlikten muaf:
                        // aksi halde eşlenmemiş/hatalı bir endpoint'in 404/500'ü, /error zincirde
                        // kimliksiz göründüğü için 401'e dönüşüyor ve SPA geçerli oturumu düşürüyor.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                // Public: a user who forgot their password has no valid token. Resets
                                // the password directly from the popup (no email link) — see AuthService.
                                "/api/v1/auth/reset-password",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/info")
                        .permitAll()
                        // Guest-reachable surface: chatting and browsing search results need no
                        // account. A logged-in user still arrives here with a populated principal
                        // (the JWT filter runs on every request); a guest arrives anonymously and is
                        // identified downstream by an opaque X-Guest-Id, never a Spring principal.
                        // Booking stays gated below so only registered users can create/retrieve a
                        // reservation ("controlled booking" invariant).
                        .requestMatchers(
                                "/api/v1/chat/**",
                                "/api/v1/hotels/**",
                                "/api/v1/flights/**")
                        .permitAll()
                        // Business module endpoints requiring an account: any authenticated user
                        // (USER or ADMIN). Path-based so the Reservation controller needs no
                        // security annotations - RBAC lives centrally in Auth.
                        .requestMatchers("/api/v1/reservations/**")
                        .hasAnyRole("USER", "ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Rate limit after authentication so buckets are keyed by the authenticated
                // principal (SecurityContextRateLimitKeyResolver), falling back to client IP.
                .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // X-Guest-Id: opaque per-visitor key the frontend sends on every tokenless request
        // (guest chat/search). Without it in the allow-list, the CORS preflight rejects any
        // request carrying the header — including /auth/login when a guest id is still in
        // localStorage — surfacing as a browser "Network Error".
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Guest-Id"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
