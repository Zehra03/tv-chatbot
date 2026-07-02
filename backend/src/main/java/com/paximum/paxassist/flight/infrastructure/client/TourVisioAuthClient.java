package com.paximum.paxassist.flight.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.paximum.paxassist.flight.infrastructure.dto.request.TourVisioLoginRequest;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioLoginResponse;

@FeignClient(name = "tourvisio-auth-client", url = "${tourvisio.url}")
public interface TourVisioAuthClient {

    @PostMapping("/authenticationservice/login")
    TourVisioLoginResponse login(@RequestBody TourVisioLoginRequest request);
}
