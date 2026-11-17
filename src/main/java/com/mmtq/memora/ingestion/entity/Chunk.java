package com.mmtq.memora.ingestion.entity;

import java.time.Instant;
import java.util.UUID;

public record Chunk(
    UUID id,
    UUID itemId,
    UUID userId,
    int chunkIndex,
    String text,
    float[] embedding,
    String embeddingModel,
    Instant createdAt
) {
    public Chunk( UUID itemId, UUID userId, int chunkIndex, String text, float[] embedding, String embeddingModel ) {
        this( UUID.randomUUID(), itemId, userId, chunkIndex, text, embedding, embeddingModel, Instant.now() );
    }
}
