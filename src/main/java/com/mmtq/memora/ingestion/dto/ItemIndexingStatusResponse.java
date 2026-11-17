package com.mmtq.memora.ingestion.dto;

import com.mmtq.memora.ingestion.enums.JobStatus;
import com.mmtq.memora.item.enums.ItemStatus;

import java.time.Instant;

public record ItemIndexingStatusResponse(
    ItemStatus itemStatus,
    int chunkCount,
    String embeddingModel,
    Instant lastIndexedAt,
    boolean upToDate,
    LatestJobDto latestJob
) {
    public record LatestJobDto(
        JobStatus status,
        int attempts,
        int maxAttempts,
        String lastError,
        Instant nextRetryAt
    ) {}
}
