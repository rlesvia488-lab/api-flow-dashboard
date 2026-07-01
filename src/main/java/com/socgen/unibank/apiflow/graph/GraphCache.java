package com.socgen.unibank.apiflow.graph;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/** Holds the latest structural graph plus when it was last (re)built. */
@Component
public class GraphCache {

    private final AtomicReference<Graph> graph = new AtomicReference<>(Graph.empty());
    private final AtomicReference<Instant> lastRefreshed = new AtomicReference<>();

    public Graph get() {
        return graph.get();
    }

    public void set(Graph newGraph) {
        graph.set(newGraph);
        lastRefreshed.set(Instant.now());
    }

    public Instant lastRefreshed() {
        return lastRefreshed.get();
    }
}
