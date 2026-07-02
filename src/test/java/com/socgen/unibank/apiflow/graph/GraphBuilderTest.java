package com.socgen.unibank.apiflow.graph;

import com.socgen.unibank.apiflow.config.DashboardProperties;
import com.socgen.unibank.apiflow.model.ApiEdge;
import com.socgen.unibank.apiflow.model.NodeKind;
import com.socgen.unibank.apiflow.model.ServiceNode;
import com.socgen.unibank.apiflow.vault.ServiceCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class GraphBuilderTest {

    private GraphBuilder graphBuilder;

    @BeforeEach
    void setUp() {
        DashboardProperties props = new DashboardProperties();
        graphBuilder = new GraphBuilder(
                new EndpointExtractor(props), new ServiceNameMatcher(props),
                new BridgeConnectorExtractor(props), props);
    }

    private static ServiceCatalog catalogOf(Map<String, Map<String, Object>> services) {
        return new ServiceCatalog() {
            @Override
            public List<String> listServiceNames() {
                return List.copyOf(services.keySet());
            }

            @Override
            public Map<String, Object> readConfig(String serviceName) {
                return services.getOrDefault(serviceName, Map.of());
            }
        };
    }

    @Test
    void build_resolvesEndpointToKnownServiceNode() {
        Map<String, Map<String, Object>> services = new LinkedHashMap<>();
        services.put("accounts-service", Map.of());
        services.put("account-identity-service", Map.of(
                "unibank.services.accounts.internal.endpoint", "https://accounts.example.com"
        ));

        Graph graph = graphBuilder.build(catalogOf(services));

        assertThat(graph.nodes()).contains(
                new ServiceNode("accounts-service", "accounts-service", NodeKind.SERVICE),
                new ServiceNode("account-identity-service", "account-identity-service", NodeKind.SERVICE)
        );
        assertThat(graph.edges()).containsExactly(
                new ApiEdge(
                        "account-identity-service->accounts-service#unibank.services.accounts.internal.endpoint",
                        "account-identity-service", "accounts-service",
                        "unibank.services.accounts.internal.endpoint", "https://accounts.example.com")
        );
        assertThat(graph.urls()).containsExactly("https://accounts.example.com");
    }

    @Test
    void build_dropsEndpointsThatDoNotResolveToAKnownService() {
        Map<String, Map<String, Object>> services = new LinkedHashMap<>();
        services.put("notifications-service", Map.of(
                "unibank.services.sms.gateway.endpoint", "https://sms-gateway.example.com"
        ));

        Graph graph = graphBuilder.build(catalogOf(services));

        assertThat(graph.nodes()).containsExactly(new ServiceNode("notifications-service", "notifications-service", NodeKind.SERVICE));
        assertThat(graph.edges()).isEmpty();
        assertThat(graph.urls()).isEmpty();
    }

    @Test
    void build_withNoServices_returnsEmptyGraph() {
        Graph graph = graphBuilder.build(catalogOf(Map.of()));

        assertThat(graph.nodes()).isEmpty();
        assertThat(graph.edges()).isEmpty();
        assertThat(graph.urls()).isEmpty();
    }

    @Test
    void build_excludesConfiguredServiceEntirely() {
        Map<String, Map<String, Object>> services = new LinkedHashMap<>();
        services.put("VAULT_L1", Map.of(
                "unibank.services.accounts.internal.endpoint", "https://accounts.example.com"
        ));
        services.put("accounts-service", Map.of(
                "unibank.services.vault.l1.endpoint", "https://vault-l1.example.com"
        ));

        Graph graph = graphBuilder.build(catalogOf(services));

        assertThat(graph.nodes()).containsExactly(new ServiceNode("accounts-service", "accounts-service", NodeKind.SERVICE));
        assertThat(graph.edges()).isEmpty();
        assertThat(graph.urls()).isEmpty();
    }

    @Test
    void build_excludedServiceMatchIsCaseInsensitive() {
        Map<String, Map<String, Object>> services = new LinkedHashMap<>();
        services.put("vault_l1", Map.of());

        Graph graph = graphBuilder.build(catalogOf(services));

        assertThat(graph.nodes()).isEmpty();
    }

    @Test
    void build_graphsBridgeConnectorCountriesAsCountryNodes() {
        Map<String, Object> amplitudeV11 = Map.of(
                "BF", Map.of("url", "https://aif.gslb.com:443/burp", "userCode", "USER_SMG"),
                "INTEROP_GIMAC", Map.of(
                        "CM", Map.of("url", "https://aif.gslb.com:443/camp/", "userCode", "USER_SMG")
                ),
                "defaults", Map.of("delayInMillis", "100", "maxRetries", "0")
        );

        Map<String, Map<String, Object>> services = new LinkedHashMap<>();
        services.put("bridge-amplitude-service", Map.of(
                "unibank.bridge.connectors.amplitude_v11", amplitudeV11
        ));

        Graph graph = graphBuilder.build(catalogOf(services));

        assertThat(graph.nodes()).contains(
                new ServiceNode("bridge-amplitude-service", "bridge-amplitude-service", NodeKind.SERVICE),
                new ServiceNode("country:BF", "BF", NodeKind.COUNTRY),
                new ServiceNode("country:CM", "CM", NodeKind.COUNTRY)
        );
        assertThat(graph.edges()).extracting(ApiEdge::source, ApiEdge::target, ApiEdge::url)
                .containsExactlyInAnyOrder(
                        tuple("bridge-amplitude-service", "country:BF", "https://aif.gslb.com:443/burp"),
                        tuple("bridge-amplitude-service", "country:CM", "https://aif.gslb.com:443/camp/")
                );
        // "defaults" is tuning config, not a country endpoint - must never appear.
        assertThat(graph.nodes()).noneMatch(n -> n.id().equalsIgnoreCase("country:defaults"));
    }

    @Test
    void build_mergesSameCountryAcrossMultipleConnectorsIntoOneNode() {
        Map<String, Map<String, Object>> services = new LinkedHashMap<>();
        services.put("bridge-amplitude-service", Map.of(
                "unibank.bridge.connectors.amplitude_v10", Map.of(
                        "GQ", Map.of("url", "https://a.example.com/aif/gq")
                ),
                "unibank.bridge.connectors.amplitude_v11", Map.of(
                        "GQ", Map.of("url", "https://b.example.com/aif/gq")
                )
        ));

        Graph graph = graphBuilder.build(catalogOf(services));

        assertThat(graph.nodes()).filteredOn(n -> n.id().equals("country:GQ")).hasSize(1);
        assertThat(graph.edges()).hasSize(2);
        assertThat(graph.edges()).allMatch(e -> e.target().equals("country:GQ"));
    }
}
