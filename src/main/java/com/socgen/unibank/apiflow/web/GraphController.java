package com.socgen.unibank.apiflow.web;

import com.socgen.unibank.apiflow.graph.Graph;
import com.socgen.unibank.apiflow.graph.GraphCache;
import com.socgen.unibank.apiflow.health.HealthChecker;
import com.socgen.unibank.apiflow.model.EndpointStatus;
import com.socgen.unibank.apiflow.model.GraphSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
public class GraphController {

    private final GraphCache graphCache;
    private final HealthChecker healthChecker;

    public GraphController(GraphCache graphCache, HealthChecker healthChecker) {
        this.graphCache = graphCache;
        this.healthChecker = healthChecker;
    }

    @GetMapping("/api/graph")
    public GraphSnapshot graph() {
        Graph graph = graphCache.get();
        Map<String, EndpointStatus> statuses = new HashMap<>();
        for (String url : graph.urls()) {
            statuses.put(url, healthChecker.statusFor(url));
        }
        Instant generatedAt = graphCache.lastRefreshed() != null ? graphCache.lastRefreshed() : Instant.now();
        return new GraphSnapshot(graph.nodes(), graph.edges(), statuses, generatedAt);
    }
}
