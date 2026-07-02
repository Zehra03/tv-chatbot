package com.paximum.paxassist.flight.infrastructure.client;

import org.springframework.context.annotation.Bean;

public class TourVisioFlightClientConfig {

    @Bean
    public TourVisioAuthRequestInterceptor tourVisioAuthRequestInterceptor(TourVisioTokenProvider tokenProvider) {
        return new TourVisioAuthRequestInterceptor(tokenProvider);
    }
}
