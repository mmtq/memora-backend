package com.mmtq.memora.item.dto;

import com.mmtq.memora.document.dto.DocumentResponse;
import com.mmtq.memora.item.enums.ItemStatus;
import com.mmtq.memora.item.enums.ItemType;
import com.mmtq.memora.item.model.Item;

import java.time.Instant;
import java.util.UUID;

public record ItemResponse(
    UUID id,
    UUID userId,
    ItemType type,
    String title,
    String description,
    String plainText,
    ItemStatus status,
    Instant createdAt,
    Instant updatedAt,
    DocumentResponse document
) {
    public static ItemResponse from( Item item ) {
        if ( item == null ) {
            return null;
        }
        return new ItemResponse(
            item.getId(),
            item.getUserId(),
            item.getType(),
            item.getTitle(),
            item.getDescription(),
            item.getPlainText(),
            item.getStatus(),
            item.getCreatedAt(),
            item.getUpdatedAt(),
            item.getDocument() != null ? DocumentResponse.from( item.getDocument() ) : null
        );
    }
}
