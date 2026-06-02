package com.nyle.kra.revenue.resolution;

import java.util.UUID;

import com.nyle.kra.revenue.graph.GraphIntelligenceService;
import com.nyle.kra.revenue.graph.TaxpayerGraphResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/taxpayers")
public class TaxpayerProfileController {

    private final TaxpayerProfileService taxpayerProfileService;
    private final GraphIntelligenceService graphIntelligenceService;

    public TaxpayerProfileController(
            TaxpayerProfileService taxpayerProfileService,
            GraphIntelligenceService graphIntelligenceService
    ) {
        this.taxpayerProfileService = taxpayerProfileService;
        this.graphIntelligenceService = graphIntelligenceService;
    }

    @GetMapping("/{id}/profile")
    public TaxpayerProfileResponse profile(@PathVariable UUID id) {
        return taxpayerProfileService.profile(id);
    }

    @GetMapping("/{id}/graph")
    public TaxpayerGraphResponse graph(@PathVariable UUID id) {
        return graphIntelligenceService.taxpayerGraph(id);
    }
}
