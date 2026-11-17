package com.mmtq.memora.ingestion.dto;

import java.time.Instant;
import java.util.UUID;

public interface JobSummaryProjection {
    UUID getId();
    String getType();
    UUID getTargetId();
    String getItemTitle();
    String getStatus();
    int getAttempts();
    int getMaxAttempts();
    String getLastError();
    Instant getRunAfter();
    Instant getStartedAt();
    Instant getFinishedAt();
    Instant getCreatedAt();
}
