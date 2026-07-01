package com.socgen.unibank.apiflow.graph;

import com.socgen.unibank.apiflow.config.DashboardProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class EndpointExtractorTest {

    private EndpointExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new EndpointExtractor(new DashboardProperties());
    }

    @Test
    void extract_findsTopLevelEndpointKeys() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("logging.level.root", "INFO");
        config.put("unibank.services.accounts.internal.endpoint", "https://abb-accounts-service.example.com");
        config.put("unibank.services.bridge.amplitude.endpoint", "https://abb-bridge-amplitude.example.com/");

        List<EndpointExtractor.RawEndpoint> found = extractor.extract(config);

        assertThat(found).extracting(EndpointExtractor.RawEndpoint::keyPath, EndpointExtractor.RawEndpoint::url)
                .containsExactlyInAnyOrder(
                        tuple("unibank.services.accounts.internal.endpoint", "https://abb-accounts-service.example.com"),
                        tuple("unibank.services.bridge.amplitude.endpoint", "https://abb-bridge-amplitude.example.com/")
                );
    }

    @Test
    void extract_walksNestedMapsAndBuildsDottedPath() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("bucket", "some-bucket");
        nested.put("endpoint", "https://s3.internal.example.com");
        nested.put("secret-key", "should-be-ignored-anyway-not-an-endpoint-key");

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("unibank.components.s3.private", nested);

        List<EndpointExtractor.RawEndpoint> found = extractor.extract(config);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).keyPath()).isEqualTo("unibank.components.s3.private.endpoint");
        assertThat(found.get(0).url()).isEqualTo("https://s3.internal.example.com");
    }

    @Test
    void extract_ignoresNonEndpointKeysAndNonUrlValues() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("unibank.service.version", "1.2.3");
        config.put("unibank.components.db.entities", Map.of());
        // key ends with ".endpoint" but the value isn't a URL - should not be extracted
        config.put("weird.but.not.a.url.endpoint", "not-a-url");

        assertThat(extractor.extract(config)).isEmpty();
    }

    @Test
    void extract_ignoresSecretsAndCredentialsEntirely() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("unibank.components.s3.private", Map.of(
                "access-key", "AKIA-SOMETHING",
                "secret-key", "super-secret-value",
                "endpoint", "https://s3.internal.example.com"
        ));

        List<EndpointExtractor.RawEndpoint> found = extractor.extract(config);

        assertThat(found).hasSize(1);
        assertThat(found).noneMatch(e -> e.url().contains("AKIA") || e.url().contains("super-secret"));
    }
}
