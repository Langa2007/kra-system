package com.nyle.kra.revenue.notifications;

import java.util.UUID;

public record CreateNudgeRequest(
        UUID caseId,
        UUID riskSignalId,
        String channel,
        String templateCode,
        String recipient
) {
}
