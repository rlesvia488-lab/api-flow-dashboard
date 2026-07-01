package com.socgen.unibank.apiflow.graph;

import com.socgen.unibank.apiflow.model.ApiEdge;
import com.socgen.unibank.apiflow.model.ServiceNode;

import java.util.List;
import java.util.Set;

/** Structural graph (nodes/edges), independent of live endpoint health. */
public record Graph(List<ServiceNode> nodes, List<ApiEdge> edges, Set<String> urls) {

    public static Graph empty() {
        return new Graph(List.of(), List.of(), Set.of());
    }
}
