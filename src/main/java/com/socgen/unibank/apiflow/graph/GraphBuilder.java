package com.socgen.unibank.apiflow.graph;

import com.socgen.unibank.apiflow.config.DashboardProperties;
import com.socgen.unibank.apiflow.model.ApiEdge;
import com.socgen.unibank.apiflow.model.ServiceNode;
import com.socgen.unibank.apiflow.vault.ServiceCatalog;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class GraphBuilder {

    private final EndpointExtractor extractor;
    private final ServiceNameMatcher matcher;
    private final DashboardProperties props;

    public GraphBuilder(EndpointExtractor extractor, ServiceNameMatcher matcher, DashboardProperties props) {
        this.extractor = extractor;
        this.matcher = matcher;
        this.props = props;
    }

    public Graph build(ServiceCatalog catalog) {
        List<String> serviceNames = catalog.listServiceNames().stream()
                .filter(name -> !isExcludedService(name))
                .toList();

        Map<String, String> normalizedToOriginal = new LinkedHashMap<>();
        for (String name : serviceNames) {
            normalizedToOriginal.put(matcher.normalizeServiceName(name), name);
        }

        Map<String, ServiceNode> nodes = new LinkedHashMap<>();
        for (String name : serviceNames) {
            nodes.put(name, new ServiceNode(name, name));
        }

        List<ApiEdge> edges = new ArrayList<>();
        Set<String> urls = new LinkedHashSet<>();

        for (String source : serviceNames) {
            Map<String, Object> config = catalog.readConfig(source);
            for (EndpointExtractor.RawEndpoint raw : extractor.extract(config)) {
                String targetKey = matcher.extractTargetKey(raw.keyPath());
                String matched = matcher.matchService(targetKey, normalizedToOriginal);

                // Only graph calls between known services - a target that doesn't
                // resolve to a discovered service is dropped, not shown as a node.
                if (matched == null) {
                    continue;
                }

                String edgeId = source + "->" + matched + "#" + raw.keyPath();
                edges.add(new ApiEdge(edgeId, source, matched, raw.keyPath(), raw.url()));
                urls.add(raw.url());
            }
        }

        return new Graph(List.copyOf(nodes.values()), List.copyOf(edges), Set.copyOf(urls));
    }

    private boolean isExcludedService(String serviceName) {
        for (String excluded : props.getExcludedServices()) {
            if (serviceName.equalsIgnoreCase(excluded)) {
                return true;
            }
        }
        return false;
    }
}
