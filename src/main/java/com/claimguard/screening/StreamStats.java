package com.claimguard.screening;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** In-memory pipeline counters, exposed by GET /api/stream/stats. */
@Component
public class StreamStats {

    private final AtomicLong consumed = new AtomicLong();
    private final AtomicLong duplicates = new AtomicLong();
    private final AtomicLong dlt = new AtomicLong();
    private final AtomicLong alertsCreated = new AtomicLong();

    public void consumed() { consumed.incrementAndGet(); }

    public void duplicateSkipped() { duplicates.incrementAndGet(); }

    public void deadLettered() { dlt.incrementAndGet(); }

    public void alertCreated() { alertsCreated.incrementAndGet(); }

    public Map<String, Long> snapshot() {
        return Map.of("consumed", consumed.get(), "duplicatesSkipped", duplicates.get(),
                "deadLettered", dlt.get(), "alertsCreated", alertsCreated.get());
    }
}
