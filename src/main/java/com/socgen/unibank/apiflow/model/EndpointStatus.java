package com.socgen.unibank.apiflow.model;

import java.time.Instant;

public record EndpointStatus(
        String url,
        HealthState state,
        Integer statusCode,
        String errorReason,
        long latencyMs,
        Instant checkedAt
) {
    public static EndpointStatus unknown(String url) {
        return new EndpointStatus(url, HealthState.UNKNOWN, null, null, -1, null);
    }
}
