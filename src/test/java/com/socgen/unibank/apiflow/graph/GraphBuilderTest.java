package com.socgen.unibank.apiflow.graph;

import com.socgen.unibank.apiflow.config.DashboardProperties;
import com.socgen.unibank.apiflow.model.ApiEdge;
import com.socgen.unibank.apiflow.model.NodeType;
import com.socgen.unibank.apiflow.model.ServiceNode;
import com.socgen.unibank.apiflow.vault.ServiceCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GraphBuilderTest {

    private GraphBuilder graphBuilder;

    @BeforeEach
    void setUp() {
        DashboardProperties props = new DashboardProperties();
        graphBuilder = new GraphBuilder(new EndpointExtractor(props), new ServiceNameMatcher(props));
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
                new ServiceNode("accounts-service", "accounts-service", NodeType.INTERNAL),
                new ServiceNode("account-identity-service", "account-identity-service", NodeType.INTERNAL)
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
    void build_marksUnresolvedTargetAsExternalNodeInsteadOfDropping() {
        Map<String, Map<String, Object>> services = new LinkedHashMap<>();
        services.put("notifications-service", Map.of(
                "unibank.services.sms.gateway.endpoint", "https://sms-gateway.example.com"
        ));

        Graph graph = graphBuilder.build(catalogOf(services));

        assertThat(graph.nodes()).contains(new ServiceNode("external:sms.gateway", "sms.gateway", NodeType.EXTERNAL));
        assertThat(graph.edges()).hasSize(1);
        assertThat(graph.edges().get(0).target()).isEqualTo("external:sms.gateway");
    }

    @Test
    void build_withNoServices_returnsEmptyGraph() {
        Graph graph = graphBuilder.build(catalogOf(Map.of()));

        assertThat(graph.nodes()).isEmpty();
        assertThat(graph.edges()).isEmpty();
        assertThat(graph.urls()).isEmpty();
    }
}
