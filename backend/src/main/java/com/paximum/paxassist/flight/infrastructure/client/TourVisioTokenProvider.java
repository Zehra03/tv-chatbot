package com.paximum.paxassist.flight.infrastructure.client;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paximum.paxassist.flight.config.TourVisioProperties;
import com.paximum.paxassist.flight.infrastructure.dto.request.TourVisioLoginRequest;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioLoginResponse;

/**
 * Supplies the TourVisio Bearer token shared by flight search/autocomplete and the reservation
 * booking client.
 *
 * <p>The token has a limited lifetime. When it lapses, TourVisio does <b>not</b> answer with HTTP
 * 401 — it returns HTTP 200 with a {@code TokenRequired} business failure (autocomplete just comes
 * back empty). A reactive "invalidate on 401" strategy therefore never fires, so a stale token gets
 * reused indefinitely: flight autocomplete resolves nothing ("no flights") and every booking is
 * refused. To avoid that we refresh <b>proactively</b> from the login response's {@code expiresOn},
 * mirroring the hotel module's {@code TourVisioHotelApiClientImpl}. {@link #invalidate()} still
 * exists for the 401 paths that can catch it.
 */
@Component
public class TourVisioTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(TourVisioTokenProvider.class);

    // Re-login this long before the stated expiry so an in-flight request can't cross the boundary
    // with a token TourVisio is about to reject.
    private static final long EXPIRY_SAFETY_MARGIN_SECONDS = 60;
    // Fallback lifetime when TourVisio omits or malforms expiresOn: short enough that a stale token
    // can never linger, avoiding a silent regression back to the never-refresh behaviour.
    private static final long FALLBACK_TTL_SECONDS = 3600;

    private final TourVisioAuthClient authClient;
    private final TourVisioProperties tourVisioProperties;
    private volatile String cachedToken;
    private volatile Instant tokenExpiry;

    public TourVisioTokenProvider(TourVisioAuthClient authClient, TourVisioProperties tourVisioProperties) {
        this.authClient = authClient;
        this.tourVisioProperties = tourVisioProperties;
    }

    public synchronized String getToken() {
        if (cachedToken == null || isExpired()) {
            login();
        }
        return cachedToken;
    }

    public synchronized void invalidate() {
        cachedToken = null;
        tokenExpiry = null;
    }

    private boolean isExpired() {
        return tokenExpiry == null
                || !Instant.now().isBefore(tokenExpiry.minusSeconds(EXPIRY_SAFETY_MARGIN_SECONDS));
    }

    private void login() {
        TourVisioLoginRequest request = new TourVisioLoginRequest(
                tourVisioProperties.agency(),
                tourVisioProperties.user(),
                tourVisioProperties.password());
        TourVisioLoginResponse response = authClient.login(request);
        if (response == null || response.header() == null || !response.header().success()
                || response.body() == null || response.body().token() == null) {
            throw new TourVisioSearchException("TourVisio login did not return a usable token");
        }
        cachedToken = response.body().token();
        tokenExpiry = parseExpiry(response.body().expiresOn());
    }

    private static Instant parseExpiry(String expiresOn) {
        if (expiresOn != null && !expiresOn.isBlank()) {
            try {
                return Instant.parse(expiresOn);
            } catch (Exception e) {
                log.warn("TourVisio login returned an unparseable expiresOn='{}'; "
                        + "falling back to a {}s token lifetime", expiresOn, FALLBACK_TTL_SECONDS);
            }
        }
        return Instant.now().plusSeconds(FALLBACK_TTL_SECONDS);
    }
}
