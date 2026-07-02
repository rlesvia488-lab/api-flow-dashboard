package com.socgen.unibank.apiflow.model;

public enum NodeKind {
    /** A discovered Vault service. */
    SERVICE,
    /** A per-country partner/bridge-connector endpoint (unibank.bridge.connectors.*), not a discovered service. */
    COUNTRY
}
