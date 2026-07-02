package com.paximum.paxassist.flight.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "com.paximum.paxassist.flight.infrastructure.client")
@EnableConfigurationProperties(TourVisioProperties.class)
public class FlightFeignConfig {
}