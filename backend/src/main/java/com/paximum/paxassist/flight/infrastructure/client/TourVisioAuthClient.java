package com.paximum.paxassist.flight.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.paximum.paxassist.flight.infrastructure.dto.request.TourVisioLoginRequest;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioLoginResponse;

// path="/api" matches the documented method path (/api/authenticationservice/login) and keeps
// tourvisio.url as the host+version base (.../v2), consistent with the hotel client.
@FeignClient(name = "tourvisio-auth-client", url = "${tourvisio.url}", path = "/api")
public interface TourVisioAuthClient {

    @PostMapping("/authenticationservice/login")
    TourVisioLoginResponse login(@RequestBody TourVisioLoginRequest request);
}
