package com.paximum.paxassist.reservation.infrastructure.tourvisio.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for the reservation module's TourVisio booking calls.
 *
 * <p><b>Client choice — Spring {@link RestClient} over the JDK {@link HttpClient}.</b>
 * <ul>
 *   <li><b>RestClient</b> (not WebClient): the booking transaction flow is strictly
 *       synchronous/sequential (BeginTransaction → … → CommitTransaction), so a blocking
 *       client is the right fit; WebClient would drag in {@code spring-webflux} and a
 *       reactive programming model we don't need.</li>
 *   <li><b>JDK {@link HttpClient} request factory</b>: it maintains an internal, reusable
 *       HTTP/1.1 keep-alive (and HTTP/2) <b>connection pool</b> and supports per-request
 *       connect/read timeouts — satisfying the "pooling + timeout policy" requirement
 *       <i>without adding a dependency</i>. Apache HttpClient5 offers richer pool tuning
 *       but is not on the classpath, and adding it would mean editing the shared
 *       {@code pom.xml} — outside this ticket's reservation-only scope.</li>
 * </ul>
 *
 * Base URL and credentials are read from the existing {@code tourvisio.*} config
 * (env-driven; same properties the Flight module already uses) — never hardcoded.
 */
@Configuration
public class TourVisioBookingClientConfig {

    public static final String BOOKING_REST_CLIENT = "tourVisioBookingRestClient";

    @Bean(BOOKING_REST_CLIENT)
    public RestClient tourVisioBookingRestClient(
            @Value("${tourvisio.url}") String baseUrl,
            @Value("${tourvisio.booking.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${tourvisio.booking.read-timeout-ms:20000}") long readTimeoutMs) {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(normalizeBaseUrl(baseUrl))
                .requestFactory(requestFactory)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /**
     * Trims a trailing slash so leading-slash endpoint paths concatenate predictably
     * (the env value may end with {@code /v2/api/} or {@code /v2/api}).
     */
    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl != null && baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
