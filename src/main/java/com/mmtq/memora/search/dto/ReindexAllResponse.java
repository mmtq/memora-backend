package com.mmtq.memora.search.dto;

public record ReindexAllResponse(
    int itemsQueued,
    String message
) {}
