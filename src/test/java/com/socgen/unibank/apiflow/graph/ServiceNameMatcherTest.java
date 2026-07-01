package com.socgen.unibank.apiflow.graph;

import com.socgen.unibank.apiflow.config.DashboardProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceNameMatcherTest {

    private ServiceNameMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new ServiceNameMatcher(new DashboardProperties());
    }

    @Test
    void normalizeServiceName_stripsServiceSuffixAndDotsHyphens() {
        assertThat(matcher.normalizeServiceName("accounts-service")).isEqualTo("accounts");
        assertThat(matcher.normalizeServiceName("account-identity-service")).isEqualTo("account.identity");
        assertThat(matcher.normalizeServiceName("bridge_amplitude_service")).isEqualTo("bridge.amplitude");
        assertThat(matcher.normalizeServiceName("audit-trail.service")).isEqualTo("audit.trail");
    }

    @Test
    void normalizeServiceName_leavesNameWithoutKnownSuffixUntouched() {
        assertThat(matcher.normalizeServiceName("gateway")).isEqualTo("gateway");
    }

    @Test
    void extractTargetKey_stripsPrefixAndEndpointSuffix() {
        assertThat(matcher.extractTargetKey("unibank.services.accounts.internal.endpoint"))
                .isEqualTo("accounts.internal");
        assertThat(matcher.extractTargetKey("unibank.services.bridge.amplitude.endpoint"))
                .isEqualTo("bridge.amplitude");
    }

    @Test
    void extractTargetKey_withoutConfiguredPrefixOnlyStripsSuffix() {
        assertThat(matcher.extractTargetKey("some.other.namespace.endpoint"))
                .isEqualTo("some.other.namespace");
    }

    @Test
    void matchService_prefersExactMatch() {
        Map<String, String> candidates = new LinkedHashMap<>();
        candidates.put("accounts", "accounts-service");
        candidates.put("account.identity", "account-identity-service");

        assertThat(matcher.matchService("accounts", candidates)).isEqualTo("accounts-service");
    }

    @Test
    void matchService_fallsBackToLongestSharedDottedPrefix() {
        Map<String, String> candidates = new LinkedHashMap<>();
        candidates.put("accounts", "accounts-service");
        candidates.put("bridge.amplitude", "bridge-amplitude-service");

        assertThat(matcher.matchService("accounts.internal", candidates)).isEqualTo("accounts-service");
        assertThat(matcher.matchService("bridge.amplitude", candidates)).isEqualTo("bridge-amplitude-service");
    }

    @Test
    void matchService_returnsNullWhenNothingLinesUp() {
        Map<String, String> candidates = new LinkedHashMap<>();
        candidates.put("accounts", "accounts-service");

        assertThat(matcher.matchService("sms.gateway", candidates)).isNull();
    }
}
