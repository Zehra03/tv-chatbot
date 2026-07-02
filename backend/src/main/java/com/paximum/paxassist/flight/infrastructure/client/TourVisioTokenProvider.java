package com.paximum.paxassist.flight.infrastructure.client;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.flight.config.TourVisioProperties;
import com.paximum.paxassist.flight.infrastructure.dto.request.TourVisioLoginRequest;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioLoginResponse;

@Component
public class TourVisioTokenProvider {

    private final TourVisioAuthClient authClient;
    private final TourVisioProperties tourVisioProperties;
    private volatile String cachedToken;

    public TourVisioTokenProvider(TourVisioAuthClient authClient, TourVisioProperties tourVisioProperties) {
        this.authClient = authClient;
        this.tourVisioProperties = tourVisioProperties;
    }

    public synchronized String getToken() {
        if (cachedToken == null) {
            cachedToken = login();
        }
        return cachedToken;
    }

    public synchronized void invalidate() {
        cachedToken = null;
    }

    private String login() {
        TourVisioLoginRequest request = new TourVisioLoginRequest(
                tourVisioProperties.agency(),
                tourVisioProperties.user(),
                tourVisioProperties.password());
        TourVisioLoginResponse response = authClient.login(request);
        if (response == null || response.header() == null || !response.header().success()
                || response.body() == null || response.body().token() == null) {
            throw new TourVisioSearchException("TourVisio login did not return a usable token");
        }
        return response.body().token();
    }
}
