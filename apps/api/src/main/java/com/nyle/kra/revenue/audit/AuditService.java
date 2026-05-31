package com.nyle.kra.revenue.audit;

import java.util.Optional;
import java.util.Map;
import java.util.UUID;

import com.nyle.kra.revenue.identity.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    public static final String LOGIN_SUCCESS = "AUTH_LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE = "AUTH_LOGIN_FAILURE";
    public static final String SENSITIVE_ACCESS = "SENSITIVE_API_ACCESS";
    public static final String DATA_SOURCE_REGISTERED = "DATA_SOURCE_REGISTERED";
    public static final String INGESTION_JOB_IMPORTED = "INGESTION_JOB_IMPORTED";
    public static final String INGESTION_JOB_DUPLICATE = "INGESTION_JOB_DUPLICATE";
    public static final String ENTITY_RESOLUTION_RUN = "ENTITY_RESOLUTION_RUN";

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(
            String action,
            Optional<AppUser> actor,
            String entityType,
            UUID entityId,
            HttpServletRequest request,
            Map<String, Object> details
    ) {
        auditLogRepository.save(new AuditLog(
                actor.orElse(null),
                action,
                entityType,
                entityId,
                clientIp(request),
                request.getHeader("User-Agent"),
                details
        ));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
