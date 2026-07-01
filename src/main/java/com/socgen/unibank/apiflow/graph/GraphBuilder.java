package com.socgen.unibank.apiflow.graph;

import com.socgen.unibank.apiflow.model.ApiEdge;
import com.socgen.unibank.apiflow.model.NodeType;
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

    public GraphBuilder(EndpointExtractor extractor, ServiceNameMatcher matcher) {
        this.extractor = extractor;
        this.matcher = matcher;
    }

    public Graph build(ServiceCatalog catalog) {
        List<String> serviceNames = catalog.listServiceNames();

        Map<String, String> normalizedToOriginal = new LinkedHashMap<>();
        for (String name : serviceNames) {
            normalizedToOriginal.put(matcher.normalizeServiceName(name), name);
        }

        Map<String, ServiceNode> nodes = new LinkedHashMap<>();
        for (String name : serviceNames) {
            nodes.put(name, new ServiceNode(name, name, NodeType.INTERNAL));
        }

        List<ApiEdge> edges = new ArrayList<>();
        Set<String> urls = new LinkedHashSet<>();

        for (String source : serviceNames) {
            Map<String, Object> config = catalog.readConfig(source);
            for (EndpointExtractor.RawEndpoint raw : extractor.extract(config)) {
                String targetKey = matcher.extractTargetKey(raw.keyPath());
                String matched = matcher.matchService(targetKey, normalizedToOriginal);

                String targetNodeId;
                if (matched != null) {
                    targetNodeId = matched;
                } else {
                    targetNodeId = "external:" + targetKey;
                    nodes.putIfAbsent(targetNodeId, new ServiceNode(targetNodeId, targetKey, NodeType.EXTERNAL));
                }

                String edgeId = source + "->" + targetNodeId + "#" + raw.keyPath();
                edges.add(new ApiEdge(edgeId, source, targetNodeId, raw.keyPath(), raw.url()));
                urls.add(raw.url());
            }
        }

        return new Graph(List.copyOf(nodes.values()), List.copyOf(edges), Set.copyOf(urls));
    }
}
