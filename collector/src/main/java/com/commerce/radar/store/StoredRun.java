package com.commerce.radar.store;

import java.time.Instant;

public record StoredRun(
        long id,
        String hybrisHome,
        String logPath,
        Instant startedAt,
        Instant endedAt,
        String mode
) {
}
