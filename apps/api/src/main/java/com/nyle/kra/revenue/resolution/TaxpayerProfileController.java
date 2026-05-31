package com.nyle.kra.revenue.resolution;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/taxpayers")
public class TaxpayerProfileController {

    private final TaxpayerProfileService taxpayerProfileService;

    public TaxpayerProfileController(TaxpayerProfileService taxpayerProfileService) {
        this.taxpayerProfileService = taxpayerProfileService;
    }

    @GetMapping("/{id}/profile")
    public TaxpayerProfileResponse profile(@PathVariable UUID id) {
        return taxpayerProfileService.profile(id);
    }
}
