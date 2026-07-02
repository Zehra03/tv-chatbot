package com.paximum.paxassist.flight.infrastructure.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;

public class TourVisioAuthRequestInterceptor implements RequestInterceptor {

    private final TourVisioTokenProvider tokenProvider;

    public TourVisioAuthRequestInterceptor(TourVisioTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void apply(RequestTemplate template) {
        template.header("Authorization", "Bearer " + tokenProvider.getToken());
    }
}
