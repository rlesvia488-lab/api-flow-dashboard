package com.socgen.unibank.apiflow.model;

/**
 * A discovered "A calls B" relationship: service {@code source} declares an
 * endpoint URL under config key {@code keyPath} that resolves to {@code target}.
 */
public record ApiEdge(String id, String source, String target, String keyPath, String url) {
}
