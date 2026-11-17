package com.mmtq.memora.document.storage;

public record StoredFile(
    String storageKey,
    String originalName,
    String mimeType,
    long sizeBytes,
    String contentHash
) {}
