package com.openslo.repository.dto;

import java.time.Instant;
import java.util.Map;

public record OpenSloSummaryDto(
    String id,
    String logicalKey,
    String apiVersion,
    String kind,
    String name,
    String displayName,
    int version,
    boolean stale,
    Instant createdAt,
    String createdBy
) {}
