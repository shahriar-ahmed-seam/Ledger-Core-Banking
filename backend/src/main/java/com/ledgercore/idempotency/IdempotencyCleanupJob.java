package com.ledgercore.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically purges idempotency records beyond the retention window (Requirement 8.6).
 */
@Component
public class IdempotencyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);

    private final IdempotencyService idempotencyService;

    public IdempotencyCleanupJob(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    /** Runs hourly; well within the >= 24h retention guarantee. */
    @Scheduled(fixedDelayString = "PT1H")
    public void purge() {
        int removed = idempotencyService.purgeExpired();
        if (removed > 0) {
            log.info("Purged {} expired idempotency records (retention {}).",
                    removed, idempotencyService.retention());
        }
    }
}
