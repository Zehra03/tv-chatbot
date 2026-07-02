package com.paximum.paxassist.audit;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default {@link AuditLogModule} until the real Log module exposes a dedicated security-audit
 * sink; writes off the calling thread so guard checks are never slowed down by logging.
 */
@Component
public class Slf4jAuditLogModule implements AuditLogModule {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    @Override
    public void logSecurityEventAsync(String message) {
        CompletableFuture.runAsync(() -> log.warn(message));
    }
}
