package com.mmtq.memora.ingestion.repository;

import com.mmtq.memora.ingestion.dto.JobSummaryProjection;
import com.mmtq.memora.ingestion.entity.Job;
import com.mmtq.memora.ingestion.enums.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {

    boolean existsByTargetIdAndStatus( UUID targetId, JobStatus status );

    @Query( value = """
            SELECT * FROM jobs
            WHERE status = 'PENDING' AND run_after <= CURRENT_TIMESTAMP
            ORDER BY run_after ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true )
    Optional<Job> claimNextPendingJob();

    @Modifying
    @Query( value = """
            UPDATE jobs
            SET status = 'PENDING',
                attempts = attempts + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE status = 'RUNNING'
              AND updated_at < (CURRENT_TIMESTAMP - INTERVAL '10 minutes')
            """, nativeQuery = true )
    int recoverStuckRunningJobs();

    long countByUserIdAndStatus( UUID userId, JobStatus status );

    @Query( "SELECT MIN(j.createdAt) FROM Job j WHERE j.userId = :userId AND j.status = :status" )
    Optional<Instant> findOldestJobCreatedAtByStatus( @Param( "userId" ) UUID userId, @Param( "status" ) JobStatus status );

    @Query( value = """
            SELECT 
                j.id AS id,
                j.type AS type,
                j.target_id AS targetId,
                i.title AS itemTitle,
                j.status AS status,
                j.attempts AS attempts,
                j.max_attempts AS maxAttempts,
                j.last_error AS lastError,
                j.run_after AS runAfter,
                j.started_at AS startedAt,
                j.finished_at AS finishedAt,
                j.created_at AS createdAt
            FROM jobs j
            LEFT JOIN items i ON j.target_id = i.id
            WHERE j.user_id = :userId
              AND (:status IS NULL OR j.status = :status)
            ORDER BY j.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM jobs j
            WHERE j.user_id = :userId
              AND (:status IS NULL OR j.status = :status)
            """,
            nativeQuery = true )
    Page<JobSummaryProjection> findJobsByUserAndStatus(
            @Param( "userId" ) UUID userId,
            @Param( "status" ) String status,
            Pageable pageable
    );

    Optional<Job> findFirstByTargetIdAndUserIdOrderByCreatedAtDesc( UUID targetId, UUID userId );

    @Query( value = """
            SELECT * FROM jobs
            WHERE target_id = :targetId
              AND user_id = :userId
              AND status = 'DONE'
            ORDER BY COALESCE(finished_at, updated_at) DESC
            LIMIT 1
            """, nativeQuery = true )
    Optional<Job> findLatestDoneJob( @Param( "targetId" ) UUID targetId, @Param( "userId" ) UUID userId );

    Optional<Job> findByIdAndUserId( UUID id, UUID userId );

    List<Job> findAllByUserIdAndStatus( UUID userId, JobStatus status );

    @Modifying
    @Query( "DELETE FROM Job j WHERE j.status = :status AND (j.finishedAt < :cutoff OR (j.finishedAt IS NULL AND j.updatedAt < :cutoff))" )
    int deleteOldDoneJobs( @Param( "status" ) JobStatus status, @Param( "cutoff" ) Instant cutoff );
}
