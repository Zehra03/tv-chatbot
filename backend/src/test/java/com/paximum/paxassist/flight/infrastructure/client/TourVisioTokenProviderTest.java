package com.paximum.paxassist.flight.infrastructure.client;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paximum.paxassist.flight.config.TourVisioProperties;
import com.paximum.paxassist.flight.infrastructure.dto.request.TourVisioLoginRequest;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioLoginResponse;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioLoginResponseBody;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioResponseHeader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TourVisioTokenProviderTest {

    private TourVisioAuthClient authClient;
    private TourVisioTokenProvider provider;

    @BeforeEach
    void setUp() {
        authClient = mock(TourVisioAuthClient.class);
        TourVisioProperties props = new TourVisioProperties(
                "https://tourvisio.example/v2", "en-US", "Europe/Istanbul", "agency", "user", "pass");
        provider = new TourVisioTokenProvider(authClient, props);
    }

    private static TourVisioLoginResponse loginResponse(String token, String expiresOn) {
        return new TourVisioLoginResponse(
                new TourVisioResponseHeader(true), new TourVisioLoginResponseBody(token, expiresOn));
    }

    @Test
    void firstCallLogsInAndCachesTokenWhileStillValid() {
        when(authClient.login(any())).thenReturn(
                loginResponse("tok-1", Instant.now().plusSeconds(3600).toString()));

        assertThat(provider.getToken()).isEqualTo("tok-1");
        assertThat(provider.getToken()).isEqualTo("tok-1");

        // A still-valid token must not trigger a second login.
        verify(authClient, times(1)).login(any());
    }

    @Test
    void refreshesProactivelyOnceTheTokenIsWithinTheExpirySafetyMargin() {
        // expiresOn already in the past -> considered expired -> every getToken() re-logs in.
        // This is the production bug: TourVisio reports the lapse as a 200 TokenRequired, never a
        // 401, so only a proactive refresh keyed on expiry recovers.
        when(authClient.login(any()))
                .thenReturn(loginResponse("stale", Instant.now().minusSeconds(10).toString()))
                .thenReturn(loginResponse("fresh", Instant.now().plusSeconds(3600).toString()));

        assertThat(provider.getToken()).isEqualTo("stale");
        assertThat(provider.getToken()).isEqualTo("fresh");
        verify(authClient, times(2)).login(any());
    }

    @Test
    void invalidateForcesReLoginOnNextCall() {
        when(authClient.login(any())).thenReturn(
                loginResponse("tok-1", Instant.now().plusSeconds(3600).toString()));

        provider.getToken();
        provider.invalidate();
        provider.getToken();

        verify(authClient, times(2)).login(any());
    }

    @Test
    void fallsBackToAShortLifetimeWhenExpiresOnIsMissingOrUnparseable() {
        // Missing expiresOn must not disable refresh (that was the never-refresh regression): the
        // token is still cached for reuse within the fallback window rather than re-fetched each call.
        when(authClient.login(any())).thenReturn(loginResponse("tok-1", null));

        assertThat(provider.getToken()).isEqualTo("tok-1");
        assertThat(provider.getToken()).isEqualTo("tok-1");
        verify(authClient, times(1)).login(any());
    }

    @Test
    void throwsWhenLoginReturnsNoUsableToken() {
        when(authClient.login(any(TourVisioLoginRequest.class))).thenReturn(
                new TourVisioLoginResponse(new TourVisioResponseHeader(false), null));

        assertThatThrownBy(() -> provider.getToken())
                .isInstanceOf(TourVisioSearchException.class);
    }
}
