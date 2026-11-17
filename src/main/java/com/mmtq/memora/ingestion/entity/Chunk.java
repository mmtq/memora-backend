package com.mmtq.memora.ingestion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table( name = "chunks" )
@Getter
@Setter
@NoArgsConstructor
public class Chunk {

    @Id
    @Column( name = "id", nullable = false )
    private UUID id;

    @Column( name = "item_id", nullable = false )
    private UUID itemId;

    @Column( name = "user_id", nullable = false )
    private UUID userId;

    @Column( name = "chunk_index", nullable = false )
    private int chunkIndex;

    @Column( name = "text", columnDefinition = "TEXT", nullable = false )
    private String text;

    @Column( name = "embedding_model", nullable = false )
    private String embeddingModel;

    @Column( name = "created_at", nullable = false, updatable = false )
    private Instant createdAt;

    @Transient
    private float[] embedding;

    public Chunk( UUID itemId, UUID userId, int chunkIndex, String text, float[] embedding, String embeddingModel ) {
        this.id = UUID.randomUUID();
        this.itemId = itemId;
        this.userId = userId;
        this.chunkIndex = chunkIndex;
        this.text = text;
        this.embedding = embedding;
        this.embeddingModel = embeddingModel;
        this.createdAt = Instant.now();
    }
}
