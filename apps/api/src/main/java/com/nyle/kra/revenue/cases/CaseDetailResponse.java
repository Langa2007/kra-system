package com.nyle.kra.revenue.cases;

import java.util.List;

public record CaseDetailResponse(
        CaseResponse detail,
        List<CaseEventResponse> events,
        List<EvidencePackResponse> evidencePacks
) {
}
