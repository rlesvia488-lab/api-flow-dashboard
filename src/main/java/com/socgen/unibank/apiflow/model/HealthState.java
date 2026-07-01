package com.socgen.unibank.apiflow.model;

public enum HealthState {
    UP,       // 2xx
    DEGRADED, // reachable but 3xx/401/403/404 - server answered, just not "ok"
    DOWN,     // 5xx, timeout, connection refused, DNS failure, TLS failure
    UNKNOWN   // not checked yet
}
