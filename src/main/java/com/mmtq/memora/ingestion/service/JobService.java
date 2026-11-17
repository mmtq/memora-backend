package com.mmtq.memora.ingestion.service;

import com.mmtq.memora.ingestion.entity.Job;
import com.mmtq.memora.ingestion.enums.JobStatus;
import com.mmtq.memora.ingestion.enums.JobType;
import com.mmtq.memora.ingestion.repository.JobRepository;
import com.mmtq.memora.item.enums.ItemStatus;
import com.mmtq.memora.item.repository.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger( JobService.class );

    private final JobRepository jobRepository;
    private final ItemRepository itemRepository;

    public JobService( JobRepository jobRepository, ItemRepository itemRepository ) {
        this.jobRepository = jobRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional
    public void enqueueIndexJob( UUID itemId ) {
        if ( itemId == null ) {
            return;
        }

        if ( jobRepository.existsByTargetIdAndStatus( itemId, JobStatus.PENDING ) ) {
            log.debug( "Skipping duplicate INDEX_ITEM job creation, pending job already exists for item: {}", itemId );
            return;
        }

        Job job = new Job( JobType.INDEX_ITEM.name(), itemId );
        jobRepository.save( job );
        log.info( "Enqueued INDEX_ITEM job {} for item {}", job.getId(), itemId );
    }

    @Transactional
    public Optional<Job> claimNextJob() {
        Optional<Job> opt = jobRepository.claimNextPendingJob();
        if ( opt.isPresent() ) {
            Job job = opt.get();
            job.setStatus( JobStatus.RUNNING );
            job.setUpdatedAt( Instant.now() );
            jobRepository.save( job );
            return Optional.of( job );
        }
        return Optional.empty();
    }

    @Transactional
    public void recoverStuckJobs() {
        int recovered = jobRepository.recoverStuckRunningJobs();
        if ( recovered > 0 ) {
            log.warn( "Recovered {} jobs stuck in RUNNING for > 10 minutes back to PENDING", recovered );
        }
    }

    @Transactional
    public void markDone( UUID jobId ) {
        jobRepository.findById( jobId ).ifPresent( job -> {
            job.setStatus( JobStatus.DONE );
            job.setUpdatedAt( Instant.now() );
            jobRepository.save( job );
            log.info( "Job {} marked as DONE", jobId );
        } );
    }

    @Transactional
    public void handleFailure( UUID jobId, Exception ex ) {
        jobRepository.findById( jobId ).ifPresent( job -> {
            int newAttempts = job.getAttempts() + 1;
            job.setAttempts( newAttempts );
            String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            job.setLastError( errorMsg );
            job.setUpdatedAt( Instant.now() );

            if ( newAttempts >= job.getMaxAttempts() ) {
                job.setStatus( JobStatus.FAILED );
                itemRepository.findById( job.getTargetId() ).ifPresent( item -> {
                    item.setStatus( ItemStatus.FAILED );
                    itemRepository.save( item );
                } );
                log.error( "Job {} FAILED permanently after {} attempts: {}", jobId, newAttempts, errorMsg );
            } else {
                long backoffSeconds = ( long ) Math.pow( 2, newAttempts ) * 2L;
                job.setRunAfter( Instant.now().plusSeconds( backoffSeconds ) );
                job.setStatus( JobStatus.PENDING );
                log.warn( "Job {} failed (attempt {}/{}), retrying in {}s: {}", jobId, newAttempts, job.getMaxAttempts(), backoffSeconds, errorMsg );
            }

            jobRepository.save( job );
        } );
    }
}
