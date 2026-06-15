package com.openslo.repository.dto;

import java.time.Instant;
import java.util.Map;

public record OpenSloDetailDto(
    String id,
    String logicalKey,
    int version,
    boolean stale,
    Map<String, Object> content,
    Instant createdAt,
    String createdBy
) {}
