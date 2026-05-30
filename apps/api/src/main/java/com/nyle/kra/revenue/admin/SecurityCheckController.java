package com.nyle.kra.revenue.admin;

import java.util.Map;
import java.util.Optional;

import com.nyle.kra.revenue.audit.AuditService;
import com.nyle.kra.revenue.identity.AppUser;
import com.nyle.kra.revenue.identity.AppUserRepository;
import com.nyle.kra.revenue.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class SecurityCheckController {

    private final AppUserRepository appUserRepository;
    private final AuditService auditService;

    public SecurityCheckController(AppUserRepository appUserRepository, AuditService auditService) {
        this.appUserRepository = appUserRepository;
        this.auditService = auditService;
    }

    @GetMapping("/security-check")
    public Map<String, Object> securityCheck(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request
    ) {
        AppUser actor = appUserRepository.findByEmailIgnoreCase(authenticatedUser.getUsername())
                .orElseThrow();
        auditService.record(
                AuditService.SENSITIVE_ACCESS,
                Optional.of(actor),
                "security_check",
                null,
                request,
                Map.of("purpose", "phase_2_authorization_gate")
        );
        return Map.of(
                "status", "AUTHORIZED",
                "actor", authenticatedUser.getUsername()
        );
    }
}
