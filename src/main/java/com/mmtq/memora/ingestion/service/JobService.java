package com.mmtq.memora.ingestion.service;

import com.mmtq.memora.common.exception.BadRequestException;
import com.mmtq.memora.common.exception.NotFoundException;
import com.mmtq.memora.ingestion.dto.IngestionStatsResponse;
import com.mmtq.memora.ingestion.dto.ItemIndexingStatusResponse;
import com.mmtq.memora.ingestion.dto.JobResponse;
import com.mmtq.memora.ingestion.dto.JobSummaryProjection;
import com.mmtq.memora.ingestion.dto.RetryFailedResponse;
import com.mmtq.memora.ingestion.entity.Job;
import com.mmtq.memora.ingestion.enums.JobStatus;
import com.mmtq.memora.ingestion.enums.JobType;
import com.mmtq.memora.ingestion.repository.ChunkRepository;
import com.mmtq.memora.ingestion.repository.JobRepository;
import com.mmtq.memora.common.response.PaginatedApiResponse;
import com.mmtq.memora.item.enums.ItemStatus;
import com.mmtq.memora.item.model.Item;
import com.mmtq.memora.item.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional( readOnly = true )
public class JobService {

    private final JobRepository jobRepository;
    private final ItemRepository itemRepository;
    private final ChunkRepository chunkRepository;
    private final WorkerStatus workerStatus;

    @Transactional
    public void enqueueIndexJob( UUID itemId, UUID userId ) {
        if ( itemId == null ) {
            return;
        }

        if ( jobRepository.existsByTargetIdAndStatus( itemId, JobStatus.PENDING ) ) {
            log.debug( "Skipping duplicate INDEX_ITEM job creation, pending job already exists for item: {}", itemId );
            return;
        }

        UUID resolvedUserId = userId;
        if ( resolvedUserId == null ) {
            resolvedUserId = itemRepository.findById( itemId ).map( Item::getUserId ).orElse( null );
        }

        if ( resolvedUserId == null ) {
            log.warn( "Cannot enqueue job: user_id could not be determined for item {}", itemId );
            return;
        }

        Job job = new Job( JobType.INDEX_ITEM.name(), itemId, resolvedUserId );
        jobRepository.save( job );
        log.info( "Enqueued INDEX_ITEM job {} for item {} (user: {})", job.getId(), itemId, resolvedUserId );
    }

    @Transactional
    public void enqueueIndexJob( UUID itemId ) {
        enqueueIndexJob( itemId, null );
    }

    @Transactional
    public Optional<Job> claimNextJob() {
        Optional<Job> opt = jobRepository.claimNextPendingJob();
        if ( opt.isPresent() ) {
            Job job = opt.get();
            job.setStatus( JobStatus.RUNNING );
            job.setAttempts( job.getAttempts() + 1 );
            job.setStartedAt( Instant.now() );
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
            job.setFinishedAt( Instant.now() );
            job.setUpdatedAt( Instant.now() );
            jobRepository.save( job );
            log.info( "Job {} marked as DONE", jobId );
        } );
    }

    @Transactional
    public void handleFailure( UUID jobId, Exception ex ) {
        jobRepository.findById( jobId ).ifPresent( job -> {
            int attempts = job.getAttempts();
            String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            job.setLastError( errorMsg );
            job.setUpdatedAt( Instant.now() );

            if ( attempts >= job.getMaxAttempts() ) {
                job.setStatus( JobStatus.FAILED );
                job.setFinishedAt( Instant.now() );
                itemRepository.updateStatus( job.getTargetId(), ItemStatus.FAILED );
                log.error( "Job {} FAILED permanently after {} attempts: {}", jobId, attempts, errorMsg );
            } else {
                long backoffSeconds;
                if ( attempts == 1 ) {
                    backoffSeconds = 5L;
                } else if ( attempts == 2 ) {
                    backoffSeconds = 30L;
                } else {
                    backoffSeconds = 300L;
                }
                job.setRunAfter( Instant.now().plusSeconds( backoffSeconds ) );
                job.setStatus( JobStatus.PENDING );
                log.warn( "Job {} failed (attempt {}/{}), retrying in {}s: {}", jobId, attempts, job.getMaxAttempts(), backoffSeconds, errorMsg );
            }

            jobRepository.save( job );
        } );
    }

    public IngestionStatsResponse getStats( UUID userId ) {
        long pending = jobRepository.countByUserIdAndStatus( userId, JobStatus.PENDING );
        long running = jobRepository.countByUserIdAndStatus( userId, JobStatus.RUNNING );
        long done = jobRepository.countByUserIdAndStatus( userId, JobStatus.DONE );
        long failed = jobRepository.countByUserIdAndStatus( userId, JobStatus.FAILED );

        Long oldestPendingAgeSeconds = jobRepository.findOldestJobCreatedAtByStatus( userId, JobStatus.PENDING )
                .map( createdAt -> Duration.between( createdAt, Instant.now() ).toSeconds() )
                .orElse( null );

        long processingItems = itemRepository.countByUserIdAndStatus( userId, ItemStatus.PROCESSING );
        long readyItems = itemRepository.countByUserIdAndStatus( userId, ItemStatus.READY );
        long failedItems = itemRepository.countByUserIdAndStatus( userId, ItemStatus.FAILED );

        long totalChunks = chunkRepository.countByUserId( userId );

        return new IngestionStatsResponse(
                new IngestionStatsResponse.JobCountsDto( pending, running, done, failed ),
                oldestPendingAgeSeconds,
                new IngestionStatsResponse.ItemCountsDto( processingItems, readyItems, failedItems ),
                totalChunks,
                workerStatus.snapshot()
        );
    }

    public PaginatedApiResponse<List<JobResponse>> getJobs( UUID userId, JobStatus statusFilter, Pageable pageable ) {
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by( Sort.Direction.DESC, "created_at" )
        );

        String statusStr = statusFilter != null ? statusFilter.name() : null;
        Page<JobSummaryProjection> page = jobRepository.findJobsByUserAndStatus( userId, statusStr, sortedPageable );

        List<JobResponse> content = page.getContent().stream()
                .map( JobResponse::from )
                .toList();

        return new PaginatedApiResponse<>(
                content,
                page.getNumber(),
                page.getTotalPages(),
                page.getTotalElements()
        );
    }

    public ItemIndexingStatusResponse getItemIndexingStatus( UUID itemId, UUID userId ) {
        Item item = itemRepository.findByIdAndUserId( itemId, userId )
                .orElseThrow( () -> new NotFoundException( "Item not found with identifier: " + itemId ) );

        int chunkCount = chunkRepository.countByItemId( itemId );
        String embeddingModel = chunkRepository.findEmbeddingModelByItemId( itemId );

        Optional<Job> latestDoneJobOpt = jobRepository.findLatestDoneJob( itemId, userId );
        Instant lastIndexedAt = latestDoneJobOpt.flatMap( job -> Optional.ofNullable( job.getFinishedAt() ) ).orElse( null );

        boolean upToDate = ( lastIndexedAt != null )
                && ( item.getUpdatedAt() == null || !item.getUpdatedAt().isAfter( lastIndexedAt ) );

        Optional<Job> latestJobOpt = jobRepository.findFirstByTargetIdAndUserIdOrderByCreatedAtDesc( itemId, userId );
        ItemIndexingStatusResponse.LatestJobDto latestJobInfo = latestJobOpt.map( job -> {
            Instant nextRetryAt = ( job.getStatus() == JobStatus.PENDING ) ? job.getRunAfter() : null;
            return new ItemIndexingStatusResponse.LatestJobDto(
                    job.getStatus(),
                    job.getAttempts(),
                    job.getMaxAttempts(),
                    job.getLastError(),
                    nextRetryAt
            );
        } ).orElse( null );

        return new ItemIndexingStatusResponse(
                item.getStatus(),
                chunkCount,
                embeddingModel,
                lastIndexedAt,
                upToDate,
                latestJobInfo
        );
    }

    @Transactional
    public JobResponse retryJob( UUID jobId, UUID userId ) {
        Job job = jobRepository.findByIdAndUserId( jobId, userId )
                .orElseThrow( () -> new NotFoundException( "Job not found with identifier: " + jobId ) );

        if ( job.getStatus() != JobStatus.FAILED ) {
            throw new BadRequestException( "Only FAILED jobs can be retried" );
        }

        job.setAttempts( 0 );
        job.setStatus( JobStatus.PENDING );
        job.setRunAfter( Instant.now() );
        job.setLastError( null );
        job.setUpdatedAt( Instant.now() );
        Job saved = jobRepository.save( job );

        itemRepository.updateStatus( job.getTargetId(), ItemStatus.PROCESSING );

        String itemTitle = itemRepository.findById( job.getTargetId() ).map( Item::getTitle ).orElse( null );
        log.info( "Job {} reset to PENDING and item {} set to PROCESSING for retry", jobId, job.getTargetId() );
        return JobResponse.from( saved, itemTitle );
    }

    @Transactional
    public RetryFailedResponse retryAllFailedJobs( UUID userId ) {
        List<Job> failedJobs = jobRepository.findAllByUserIdAndStatus( userId, JobStatus.FAILED );
        Instant now = Instant.now();

        for ( Job job : failedJobs ) {
            job.setAttempts( 0 );
            job.setStatus( JobStatus.PENDING );
            job.setRunAfter( now );
            job.setLastError( null );
            job.setUpdatedAt( now );
            jobRepository.save( job );

            itemRepository.updateStatus( job.getTargetId(), ItemStatus.PROCESSING );
        }

        log.info( "Retried {} failed jobs for user {}", failedJobs.size(), userId );
        return new RetryFailedResponse( failedJobs.size(), String.format( "Retried %d failed jobs", failedJobs.size() ) );
    }

    @Transactional
    public int deleteOldDoneJobs( int retentionDays ) {
        Instant cutoff = Instant.now().minus( retentionDays, ChronoUnit.DAYS );
        return jobRepository.deleteOldDoneJobs( JobStatus.DONE, cutoff );
    }
}
