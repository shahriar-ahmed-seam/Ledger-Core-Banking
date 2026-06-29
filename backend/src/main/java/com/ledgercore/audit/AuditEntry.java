package com.ledgercore.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * An append-only audit record of a sensitive action (Requirement 11). Never updated or
 * deleted.
 */
@Entity
@Table(name = "audit_log")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "action_type", nullable = false, updatable = false)
    private String actionType;

    @Column(name = "target_id", updatable = false)
    private String targetId;

    @Column(name = "outcome", nullable = false, updatable = false)
    private String outcome;

    @Column(name = "detail", updatable = false)
    private String detail;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditEntry() {
    }

    public AuditEntry(UUID actorId, String actionType, String targetId, String outcome,
                      String detail, Instant occurredAt) {
        this.actorId = actorId;
        this.actionType = actionType;
        this.targetId = targetId;
        this.outcome = outcome;
        this.detail = detail;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActionType() {
        return actionType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
