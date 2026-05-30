package com.nyle.kra.revenue.audit;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import com.nyle.kra.revenue.identity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "actor_user_id")
    private AppUser actorUser;

    @Column(nullable = false)
    private String action;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AuditLog() {
    }

    public AuditLog(
            AppUser actorUser,
            String action,
            String entityType,
            UUID entityId,
            String ipAddress,
            String userAgent,
            Map<String, Object> details
    ) {
        this.id = UUID.randomUUID();
        this.actorUser = actorUser;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.details = details;
        this.createdAt = OffsetDateTime.now();
    }

    public String getAction() {
        return action;
    }
}
