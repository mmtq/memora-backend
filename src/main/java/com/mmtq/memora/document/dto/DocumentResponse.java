package com.mmtq.memora.document.dto;

import com.mmtq.memora.document.model.Document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
    UUID id,
    UUID itemId,
    String storageKey,
    String originalName,
    String mimeType,
    Long sizeBytes,
    String contentHash,
    Instant createdAt
) {
    public static DocumentResponse from( Document document ) {
        if ( document == null ) {
            return null;
        }
        return new DocumentResponse(
            document.getId(),
            document.getItem() != null ? document.getItem().getId() : null,
            document.getStorageKey(),
            document.getOriginalName(),
            document.getMimeType(),
            document.getSizeBytes(),
            document.getContentHash(),
            document.getCreatedAt()
        );
    }
}
