package com.nyle.kra.revenue.ingestion;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.nyle.kra.revenue.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ingestion/jobs")
public class IngestionController {

    private final IngestionService ingestionService;
    private final IngestionJobRepository ingestionJobRepository;
    private final DataQualityIssueRepository dataQualityIssueRepository;

    public IngestionController(
            IngestionService ingestionService,
            IngestionJobRepository ingestionJobRepository,
            DataQualityIssueRepository dataQualityIssueRepository
    ) {
        this.ingestionService = ingestionService;
        this.ingestionJobRepository = ingestionJobRepository;
        this.dataQualityIssueRepository = dataQualityIssueRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IngestionJobResponse create(
            @RequestParam String dataSourceCode,
            @RequestParam String targetTable,
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request
    ) {
        return IngestionJobResponse.from(ingestionService.ingest(
                dataSourceCode,
                targetTable,
                file,
                authenticatedUser,
                request
        ));
    }

    @GetMapping
    public List<IngestionJobResponse> list() {
        return ingestionJobRepository.findAllByOrderByStartedAtDesc().stream()
                .map(IngestionJobResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public IngestionJobResponse get(@PathVariable UUID id) {
        return ingestionJobRepository.findById(Objects.requireNonNull(id))
                .map(IngestionJobResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingestion job not found"));
    }

    @GetMapping("/{id}/issues")
    public List<DataQualityIssueResponse> issues(@PathVariable UUID id) {
        UUID jobId = Objects.requireNonNull(id);
        if (!ingestionJobRepository.existsById(jobId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingestion job not found");
        }
        return dataQualityIssueRepository.findByIngestionJobIdOrderByCreatedAtAsc(jobId).stream()
                .map(DataQualityIssueResponse::from)
                .toList();
    }
}
