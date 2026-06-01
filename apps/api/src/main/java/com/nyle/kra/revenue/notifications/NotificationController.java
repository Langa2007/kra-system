package com.nyle.kra.revenue.notifications;

import java.util.List;
import java.util.UUID;

import com.nyle.kra.revenue.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/templates")
    public List<NotificationTemplateResponse> templates() {
        return notificationService.templates();
    }

    @GetMapping
    public List<NotificationResponse> history(
            @RequestParam(required = false) UUID caseId,
            @RequestParam(required = false) UUID riskSignalId,
            @RequestParam(required = false) UUID taxpayerId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return notificationService.history(caseId, riskSignalId, taxpayerId, limit);
    }

    @PostMapping("/nudges")
    public NotificationResponse createNudge(
            @RequestBody CreateNudgeRequest request,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest
    ) {
        return notificationService.createNudge(request, user, httpRequest);
    }

    @PostMapping("/{id}/response")
    public NotificationResponse recordResponse(
            @PathVariable UUID id,
            @RequestBody RecordTaxpayerResponseRequest request,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest
    ) {
        return notificationService.recordResponse(id, request, user, httpRequest);
    }
}
