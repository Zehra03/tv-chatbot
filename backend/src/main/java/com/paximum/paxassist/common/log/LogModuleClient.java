package com.paximum.paxassist.common.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class LogModuleClient {

    private static final Logger log = LoggerFactory.getLogger(LogModuleClient.class);
    private final RestTemplate restTemplate;

    @Value("${app.log-service-url:http://localhost:8081/api/logs}")
    private String logServiceUrl;

    public LogModuleClient() {
        this.restTemplate = new RestTemplate();
    }

    @Async
    public void logActivity(String module, String action, String requestData, String status, String message) {
        log.info("Sending async log event: Module={}, Action={}, Status={}", module, action, status);
        try {
            Map<String, Object> logPayload = new HashMap<>();
            logPayload.put("module", module);
            logPayload.put("action", action);
            logPayload.put("requestData", requestData);
            logPayload.put("status", status);
            logPayload.put("message", message);
            logPayload.put("timestamp", java.time.Instant.now().toString());

            restTemplate.postForEntity(logServiceUrl, logPayload, Void.class);
            log.info("Log event successfully sent to log service.");
        } catch (Exception e) {
            log.warn("Failed to send log event to log service (Service might not be running yet): {}", e.getMessage());
        }
    }
}
