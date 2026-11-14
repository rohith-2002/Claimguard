package com.claimguard.api;

import com.claimguard.api.ClaimRequest;
import com.claimguard.api.ScreeningResponse;
import com.claimguard.screening.ScreeningService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/claims")
public class ScreeningController {

    private final ScreeningService screeningService;

    public ScreeningController(ScreeningService screeningService) {
        this.screeningService = screeningService;
    }

    @PostMapping("/screen")
    public ScreeningResponse screen(@Valid @RequestBody ClaimRequest claim) {
        return screeningService.screen(claim);
    }
}
