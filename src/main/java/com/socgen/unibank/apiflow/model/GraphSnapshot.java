package com.socgen.unibank.apiflow.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record GraphSnapshot(
        List<ServiceNode> nodes,
        List<ApiEdge> edges,
        Map<String, EndpointStatus> statusByUrl,
        Instant generatedAt
) {
}
