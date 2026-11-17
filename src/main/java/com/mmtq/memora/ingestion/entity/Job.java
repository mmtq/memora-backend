package com.mmtq.memora.ingestion.entity;

import com.mmtq.memora.ingestion.enums.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table( name = "jobs" )
public class Job {

    @Id
    @GeneratedValue( strategy = GenerationType.UUID )
    @Column( name = "id", nullable = false, updatable = false )
    private UUID id;

    @Column( name = "user_id", nullable = false )
    private UUID userId;

    @Column( name = "type", nullable = false, length = 50 )
    private String type;

    @Column( name = "target_id", nullable = false )
    private UUID targetId;

    @Enumerated( EnumType.STRING )
    @Column( name = "status", nullable = false, length = 50 )
    private JobStatus status;

    @Column( name = "attempts", nullable = false )
    private int attempts;

    @Column( name = "max_attempts", nullable = false )
    private int maxAttempts;

    @Column( name = "last_error", columnDefinition = "TEXT" )
    private String lastError;

    @Column( name = "run_after", nullable = false )
    private Instant runAfter;

    @Column( name = "started_at" )
    private Instant startedAt;

    @Column( name = "finished_at" )
    private Instant finishedAt;

    @Column( name = "created_at", nullable = false, updatable = false )
    private Instant createdAt;

    @Column( name = "updated_at", nullable = false )
    private Instant updatedAt;

    public Job() {
        this.status = JobStatus.PENDING;
        this.attempts = 0;
        this.maxAttempts = 3;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.runAfter = this.createdAt;
    }

    public Job( String type, UUID targetId, UUID userId ) {
        this();
        this.type = type;
        this.targetId = targetId;
        this.userId = userId;
    }

    public Job( String type, UUID targetId ) {
        this( type, targetId, null );
    }

    public UUID getId() {
        return id;
    }

    public void setId( UUID id ) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId( UUID userId ) {
        this.userId = userId;
    }

    public String getType() {
        return type;
    }

    public void setType( String type ) {
        this.type = type;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public void setTargetId( UUID targetId ) {
        this.targetId = targetId;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus( JobStatus status ) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts( int attempts ) {
        this.attempts = attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts( int maxAttempts ) {
        this.maxAttempts = maxAttempts;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError( String lastError ) {
        this.lastError = lastError;
    }

    public Instant getRunAfter() {
        return runAfter;
    }

    public void setRunAfter( Instant runAfter ) {
        this.runAfter = runAfter;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt( Instant startedAt ) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt( Instant finishedAt ) {
        this.finishedAt = finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt( Instant createdAt ) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt( Instant updatedAt ) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals( Object o ) {
        if ( this == o ) return true;
        if ( o == null || getClass() != o.getClass() ) return false;
        Job job = ( Job ) o;
        return id != null && Objects.equals( id, job.id );
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash( id ) : System.identityHashCode( this );
    }
}
