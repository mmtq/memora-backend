package com.mmtq.memora.search.dto;

import com.mmtq.memora.item.enums.ItemType;

import java.util.UUID;

public record SearchResultItem(
    UUID itemId,
    ItemType type,
    String title,
    String snippet,
    double score
) {}
