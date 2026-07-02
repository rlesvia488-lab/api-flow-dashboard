package com.socgen.unibank.apiflow.graph;

import com.socgen.unibank.apiflow.config.DashboardProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class BridgeConnectorExtractorTest {

    private BridgeConnectorExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new BridgeConnectorExtractor(new DashboardProperties());
    }

    @Test
    void extract_findsOneCountryPerConnectorEntry() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("unibank.bridge.connectors.amplitude_v10", Map.of(
                "GQ", Map.of("url", "https://abb-:445/aif/gq")
        ));

        List<BridgeConnectorExtractor.CountryEndpoint> found = extractor.extract(config);

        assertThat(found).extracting(
                        BridgeConnectorExtractor.CountryEndpoint::country,
                        BridgeConnectorExtractor.CountryEndpoint::connector,
                        BridgeConnectorExtractor.CountryEndpoint::url)
                .containsExactly(tuple("GQ", "amplitude_v10", "https://abb-:445/aif/gq"));
    }

    @Test
    void extract_skipsDefaultsBagAndUnwrapsNestedInteropGroup() {
        Map<String, Object> amplitudeV11 = new LinkedHashMap<>();
        amplitudeV11.put("BF", Map.of("url", "https://aif.gslb.com:443/burp", "userCode", "USER_SMG"));
        amplitudeV11.put("CG", Map.of("url", "https://aif.gslb.com:443/cngp/", "userCode", "USER_SMG"));
        amplitudeV11.put("INTEROP_GIMAC", Map.of(
                "CM", Map.of("url", "https://aif.gslb.com:443/camp/", "userCode", "USER_SMG")
        ));
        amplitudeV11.put("defaults", Map.of(
                "delayInMillis", "100",
                "entities.bug.balance.acticity", "MR,BJ,GN,MG,GH",
                "maxDelayInMillis", "6000",
                "maxRetries", "0",
                "transfer.flwind", "PROD"
        ));

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("unibank.bridge.connectors.amplitude_v11", amplitudeV11);

        List<BridgeConnectorExtractor.CountryEndpoint> found = extractor.extract(config);

        assertThat(found).extracting(BridgeConnectorExtractor.CountryEndpoint::country)
                .containsExactlyInAnyOrder("BF", "CG", "CM");
        assertThat(found).noneMatch(e -> e.country().equalsIgnoreCase("defaults"));
        // The nested entry keeps its own country code, not the interop group's name.
        assertThat(found).filteredOn(e -> e.country().equals("CM"))
                .extracting(BridgeConnectorExtractor.CountryEndpoint::url)
                .containsExactly("https://aif.gslb.com:443/camp/");
    }

    @Test
    void extract_ignoresConfigOutsideTheBridgeConnectorPrefix() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("unibank.components.s3.private", Map.of(
                "bucket", "some-bucket",
                "endpoint", "https://s3.internal.example.com",
                "secret-key", "super-secret"
        ));
        config.put("unibank.services.accounts.internal.endpoint", "https://accounts.example.com");

        assertThat(extractor.extract(config)).isEmpty();
    }

    @Test
    void extract_normalizesCountryCodeToUpperCase() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("unibank.bridge.connectors.amplitude_v10", Map.of(
                "gq", Map.of("url", "https://example.com/aif/gq")
        ));

        assertThat(extractor.extract(config)).extracting(BridgeConnectorExtractor.CountryEndpoint::country)
                .containsExactly("GQ");
    }

    @Test
    void extract_ignoresExcludedUrlPrefixes() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("unibank.bridge.connectors.amplitude_v10", Map.of(
                "GQ", Map.of("url", "https://pvip-internal.example.com/aif/gq"),
                "BF", Map.of("url", "https://aif.gslb.com/burp")
        ));

        assertThat(extractor.extract(config)).extracting(BridgeConnectorExtractor.CountryEndpoint::country)
                .containsExactly("BF");
    }
}
