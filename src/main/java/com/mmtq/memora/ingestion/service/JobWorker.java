package com.mmtq.memora.ingestion.service;

import com.mmtq.memora.ingestion.entity.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger( JobWorker.class );

    private final JobService jobService;
    private final ItemIndexingService itemIndexingService;

    public JobWorker( JobService jobService, ItemIndexingService itemIndexingService ) {
        this.jobService = jobService;
        this.itemIndexingService = itemIndexingService;
    }

    @Scheduled( fixedDelayString = "${app.jobs.poll-interval-ms:3000}" )
    public void pollAndProcessJob() {
        try {
            // Reclaim any jobs that have been stuck in RUNNING for over 10 minutes
            jobService.recoverStuckJobs();

            // Claim one pending job atomically
            Optional<Job> claimedJobOpt = jobService.claimNextJob();
            if ( claimedJobOpt.isEmpty() ) {
                return;
            }

            Job job = claimedJobOpt.get();
            log.debug( "Worker claimed job {} (target: {})", job.getId(), job.getTargetId() );

            try {
                itemIndexingService.processIndexItem( job.getTargetId() );
                jobService.markDone( job.getId() );
            } catch ( Exception ex ) {
                log.error( "Error processing job {}: {}", job.getId(), ex.getMessage(), ex );
                jobService.handleFailure( job.getId(), ex );
            }
        } catch ( Exception ex ) {
            log.error( "Unexpected error in JobWorker poll loop: {}", ex.getMessage(), ex );
        }
    }
}
