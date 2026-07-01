package com.socgen.unibank.apiflow.graph;

import com.socgen.unibank.apiflow.config.DashboardProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Walks a (possibly mixed flat/nested) config map and pulls out every
 * "*.endpoint" -> URL pair. Only the matched key path and URL are ever
 * extracted - the rest of the config (secrets, credentials, etc.) is never
 * touched, so nothing sensitive can leak downstream by construction.
 */
@Component
public class EndpointExtractor {

    private final DashboardProperties props;

    public EndpointExtractor(DashboardProperties props) {
        this.props = props;
    }

    public record RawEndpoint(String keyPath, String url) {
    }

    public List<RawEndpoint> extract(Map<String, Object> config) {
        List<RawEndpoint> found = new ArrayList<>();
        walk("", config, found);
        return found;
    }

    @SuppressWarnings("unchecked")
    private void walk(String prefix, Map<String, Object> node, List<RawEndpoint> found) {
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String path = prefix.isEmpty() ? key : prefix + "." + key;

            if (value instanceof Map<?, ?> nested) {
                walk(path, (Map<String, Object>) nested, found);
            } else if (value instanceof String str && path.endsWith(props.getEndpointKeySuffix()) && looksLikeUrl(str)) {
                found.add(new RawEndpoint(path, str));
            }
        }
    }

    private boolean looksLikeUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }
}
