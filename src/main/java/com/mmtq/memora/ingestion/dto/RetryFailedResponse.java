package com.mmtq.memora.ingestion.dto;

public record RetryFailedResponse(
    int retriedCount,
    String message
) {}
