package com.claimguard.api;

import com.claimguard.screening.StreamStats;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stream")
public class StatsController {

    private final StreamStats stats;

    public StatsController(StreamStats stats) {
        this.stats = stats;
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return stats.snapshot();
    }
}
