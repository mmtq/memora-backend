package com.mmtq.memora.common.event;

import java.util.UUID;

public record ItemChangedEvent(
    UUID itemId,
    UUID userId
) {}
