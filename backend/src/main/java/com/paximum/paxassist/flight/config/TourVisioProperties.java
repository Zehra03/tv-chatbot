package com.paximum.paxassist.flight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tourvisio")
public record TourVisioProperties(String url, String culture, String timezone) {
}