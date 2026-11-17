package com.mmtq.memora.ingestion.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class WorkerStatus {

    private final AtomicReference<Instant> lastPollAt = new AtomicReference<>();
    private final AtomicReference<UUID> currentJobId = new AtomicReference<>();
    private final AtomicReference<Instant> lastJobFinishedAt = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();

    public void recordPoll( Instant timestamp ) {
        this.lastPollAt.set( timestamp );
    }

    public void jobStarted( UUID jobId ) {
        this.currentJobId.set( jobId );
    }

    public void jobFinished( UUID jobId ) {
        this.currentJobId.compareAndSet( jobId, null );
        this.lastJobFinishedAt.set( Instant.now() );
    }

    public void jobFailed( UUID jobId, String error ) {
        this.currentJobId.compareAndSet( jobId, null );
        this.lastJobFinishedAt.set( Instant.now() );
        this.lastError.set( error );
    }

    public Instant getLastPollAt() {
        return lastPollAt.get();
    }

    public UUID getCurrentJobId() {
        return currentJobId.get();
    }

    public Instant getLastJobFinishedAt() {
        return lastJobFinishedAt.get();
    }

    public String getLastError() {
        return lastError.get();
    }

    public WorkerStatusSnapshot snapshot() {
        return new WorkerStatusSnapshot(
                getLastPollAt(),
                getCurrentJobId(),
                getLastJobFinishedAt(),
                getLastError()
        );
    }

    public record WorkerStatusSnapshot(
            Instant lastPollAt,
            UUID currentJobId,
            Instant lastJobFinishedAt,
            String lastError
    ) {}
}
