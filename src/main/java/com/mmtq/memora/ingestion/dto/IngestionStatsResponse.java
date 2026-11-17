package com.mmtq.memora.ingestion.dto;

import com.mmtq.memora.ingestion.service.WorkerStatus;

public record IngestionStatsResponse(
    JobCountsDto jobs,
    Long oldestPendingJobAgeSeconds,
    ItemCountsDto items,
    long totalChunks,
    WorkerStatus.WorkerStatusSnapshot worker
) {
    public record JobCountsDto(
        long pending,
        long running,
        long done,
        long failed
    ) {}

    public record ItemCountsDto(
        long processing,
        long ready,
        long failed
    ) {}
}
