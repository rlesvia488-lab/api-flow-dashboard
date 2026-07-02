package com.socgen.unibank.apiflow.vault;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canned service configs so the dashboard is demoable without live Vault
 * credentials. Mirrors the real shape (flat + nested keys, secrets that must
 * never reach the UI, several ".endpoint" keys per service, a mix of healthy
 * and failing targets, one infra/component endpoint that must NOT show up in
 * the graph since it isn't a service-to-service call, and a bridge/connector
 * block with per-country endpoints, a nested interop group, and a "defaults"
 * tuning bag that must be skipped).
 */
@Component
@ConditionalOnProperty(prefix = "vault", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DemoServiceCatalog implements ServiceCatalog {

    private final Map<String, Map<String, Object>> configs = new LinkedHashMap<>();

    public DemoServiceCatalog() {
        configs.put("account-identity-service", config(Map.of(
                "logging.level.root", "INFO",
                "unibank.services.accounts.internal.endpoint", "https://httpbingo.org/status/200",
                "unibank.services.bridge.amplitude.endpoint", "https://httpbingo.org/status/401",
                "unibank.components.s3.private", Map.of(
                        "bucket", "example-bucket-prd",
                        "endpoint", "https://s3.internal.example.com",
                        "access-key", "REDACTED",
                        "secret-key", "REDACTED"
                )
        )));

        configs.put("accounts-service", config(Map.of(
                "logging.level.root", "INFO",
                "unibank.services.notifications.endpoint", "https://httpbingo.org/status/200",
                "unibank.services.ledger.core.endpoint", "https://httpbingo.org/status/200"
        )));

        configs.put("ledger-core-service", config(Map.of(
                "logging.level.root", "WARN",
                "unibank.services.audit.trail.endpoint", "https://httpbingo.org/status/503"
        )));

        configs.put("audit-trail-service", config(Map.of(
                "logging.level.root", "INFO"
        )));

        configs.put("notifications-service", config(Map.of(
                "logging.level.root", "INFO",
                "unibank.services.bridge.amplitude.endpoint", "https://httpbingo.org/status/401"
        )));

        Map<String, Object> amplitudeV11 = new LinkedHashMap<>();
        amplitudeV11.put("BF", Map.of("url", "https://httpbingo.org/status/200", "userCode", "USER_SMG"));
        amplitudeV11.put("CI", Map.of("url", "https://httpbingo.org/status/503", "userCode", "USER_SMG"));
        amplitudeV11.put("INTEROP_GIMAC", Map.of(
                "CM", Map.of("url", "https://httpbingo.org/status/200", "userCode", "USER_SMG")
        ));
        amplitudeV11.put("defaults", Map.of(
                "delayInMillis", "100",
                "maxDelayInMillis", "6000",
                "maxRetries", "0",
                "transfer.flwind", "PROD"
        ));

        configs.put("bridge-amplitude-service", config(Map.of(
                "logging.level.root", "DEBUG",
                "unibank.services.accounts.internal.endpoint", "https://httpbingo.org/status/200",
                "unibank.bridge.connectors.amplitude_v11", amplitudeV11
        )));
    }

    private static Map<String, Object> config(Map<String, Object> entries) {
        return new LinkedHashMap<>(entries);
    }

    @Override
    public List<String> listServiceNames() {
        return List.copyOf(configs.keySet());
    }

    @Override
    public Map<String, Object> readConfig(String serviceName) {
        return configs.getOrDefault(serviceName, Map.of());
    }
}
