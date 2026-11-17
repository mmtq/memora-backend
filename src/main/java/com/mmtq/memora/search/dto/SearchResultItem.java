package com.mmtq.memora.search.dto;

import com.mmtq.memora.item.enums.ItemStatus;
import com.mmtq.memora.item.enums.ItemType;

import java.time.Instant;
import java.util.UUID;

public record SearchResultItem(
    UUID itemId,
    ItemType type,
    String title,
    String description,
    ItemStatus status,
    Instant createdAt,
    Instant updatedAt,
    String snippet,
    double score
) {}
