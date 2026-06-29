package com.ledgercore.api;

import com.ledgercore.audit.AuditEntry;
import com.ledgercore.audit.AuditService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Audit trail query endpoint (Requirement 11.3). ADMIN only.
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    public record AuditEntryResponse(long id, UUID actorId, String actionType, String targetId,
                                     String outcome, String detail, Instant occurredAt) {
        static AuditEntryResponse from(AuditEntry e) {
            return new AuditEntryResponse(e.getId(), e.getActorId(), e.getActionType(),
                    e.getTargetId(), e.getOutcome(), e.getDetail(), e.getOccurredAt());
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Envelopes.Success<List<AuditEntryResponse>> query(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long afterId,
            @RequestParam(required = false) Integer size) {
        int pageSize = size == null ? 50 : Math.max(1, Math.min(size, 500));
        List<AuditEntryResponse> entries = auditService
                .query(from, to, null, afterId == null ? 0 : afterId, pageSize)
                .stream().map(AuditEntryResponse::from).toList();
        return Envelopes.ok(entries);
    }
}
