package com.mmtq.memora.search.dto;

import java.time.Instant;
import java.util.UUID;

public interface SearchChunkProjection {
    UUID getItemId();
    String getType();
    String getTitle();
    String getDescription();
    String getStatus();
    Instant getCreatedAt();
    Instant getUpdatedAt();
    String getChunkText();
    Double getScore();
}
