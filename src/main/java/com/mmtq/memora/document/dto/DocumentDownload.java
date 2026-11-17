package com.mmtq.memora.document.dto;

import org.springframework.core.io.Resource;

public record DocumentDownload(
    Resource resource,
    String originalName,
    String mimeType,
    long sizeBytes
) {}
