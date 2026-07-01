package com.socgen.unibank.apiflow.model;

public enum NodeType {
    /** Resolved to a service the dashboard discovered under the Vault engine. */
    INTERNAL,
    /** Endpoint key didn't match any known service (e.g. a third-party/SaaS dependency). */
    EXTERNAL
}
