package com.mmtq.memora.ingestion.service;

import com.mmtq.memora.ingestion.entity.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobWorker {

    private final JobService jobService;
    private final ItemIndexingService itemIndexingService;
    private final WorkerStatus workerStatus;

    @Value( "${app.jobs.retention-days:7}" )
    private int retentionDays = 7;

    @Scheduled( fixedDelayString = "${app.jobs.poll-interval-ms:2000}" )
    public void pollAndProcessJob() {
        try {
            workerStatus.recordPoll( Instant.now() );

            // Reclaim any jobs that have been stuck in RUNNING for over 10 minutes
            jobService.recoverStuckJobs();

            // Claim one pending job atomically
            Optional<Job> claimedJobOpt = jobService.claimNextJob();
            if ( claimedJobOpt.isEmpty() ) {
                return;
            }

            Job job = claimedJobOpt.get();
            workerStatus.jobStarted( job.getId() );
            long startTime = System.currentTimeMillis();

            log.info( "Job {} started for item {}", job.getId(), job.getTargetId() );

            try {
                int chunkCount = itemIndexingService.processIndexItem( job.getTargetId() );
                jobService.markDone( job.getId() );
                workerStatus.jobFinished( job.getId() );
                long durationMs = System.currentTimeMillis() - startTime;
                log.info( "Job {} succeeded for item {} with {} chunks in {}ms", job.getId(), job.getTargetId(), chunkCount, durationMs );
            } catch ( Exception ex ) {
                long durationMs = System.currentTimeMillis() - startTime;
                String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                log.error( "Job {} failed for item {} in {}ms: {}", job.getId(), job.getTargetId(), durationMs, errorMsg );
                workerStatus.jobFailed( job.getId(), errorMsg );
                jobService.handleFailure( job.getId(), ex );
            }
        } catch ( Exception ex ) {
            log.error( "Unexpected error in JobWorker poll loop: {}", ex.getMessage(), ex );
        }
    }

    @Scheduled( cron = "${app.jobs.cleanup-cron:0 0 2 * * ?}" )
    public void cleanupOldDoneJobs() {
        try {
            int deleted = jobService.deleteOldDoneJobs( retentionDays );
            if ( deleted > 0 ) {
                log.info( "Housekeeping: Deleted {} completed jobs older than {} days", deleted, retentionDays );
            }
        } catch ( Exception ex ) {
            log.error( "Error during job cleanup: {}", ex.getMessage(), ex );
        }
    }
}
