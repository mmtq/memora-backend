package com.mmtq.memora.ingestion.repository;

import com.mmtq.memora.ingestion.entity.Job;
import com.mmtq.memora.ingestion.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
}
