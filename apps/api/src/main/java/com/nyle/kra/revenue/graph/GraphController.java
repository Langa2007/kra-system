package com.nyle.kra.revenue.graph;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final GraphIntelligenceService graphIntelligenceService;

    public GraphController(GraphIntelligenceService graphIntelligenceService) {
        this.graphIntelligenceService = graphIntelligenceService;
    }

    @PostMapping("/jobs")
    public GraphExtractionResponse extract() {
        return graphIntelligenceService.extractGraph();
    }

    @GetMapping("/clusters")
    public List<HighRiskClusterResponse> clusters(
            @RequestParam(defaultValue = "70") int minimumScore
    ) {
        return graphIntelligenceService.highRiskClusters(minimumScore);
    }
}
