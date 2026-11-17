package com.mmtq.memora.ingestion.dto;

import com.mmtq.memora.ingestion.entity.Job;
import com.mmtq.memora.ingestion.enums.JobStatus;

import java.time.Instant;
import java.util.UUID;

public record JobResponse(
    UUID id,
    String type,
    UUID targetId,
    String itemTitle,
    JobStatus status,
    int attempts,
    int maxAttempts,
    String lastError,
    Instant runAfter,
    Instant startedAt,
    Instant finishedAt
) {
    public static JobResponse from( JobSummaryProjection proj ) {
        if ( proj == null ) {
            return null;
        }
        JobStatus jobStatus = proj.getStatus() != null ? JobStatus.valueOf( proj.getStatus() ) : null;
        return new JobResponse(
            proj.getId(),
            proj.getType(),
            proj.getTargetId(),
            proj.getItemTitle(),
            jobStatus,
            proj.getAttempts(),
            proj.getMaxAttempts(),
            proj.getLastError(),
            proj.getRunAfter(),
            proj.getStartedAt(),
            proj.getFinishedAt()
        );
    }

    public static JobResponse from( Job job, String itemTitle ) {
        if ( job == null ) {
            return null;
        }
        return new JobResponse(
            job.getId(),
            job.getType(),
            job.getTargetId(),
            itemTitle,
            job.getStatus(),
            job.getAttempts(),
            job.getMaxAttempts(),
            job.getLastError(),
            job.getRunAfter(),
            job.getStartedAt(),
            job.getFinishedAt()
        );
    }
}
