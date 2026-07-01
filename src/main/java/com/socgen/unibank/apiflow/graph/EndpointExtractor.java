package com.socgen.unibank.apiflow.graph;

import com.socgen.unibank.apiflow.config.DashboardProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Walks a (possibly mixed flat/nested) config map and pulls out every
 * "*.endpoint" -> URL pair that lives under a configured service-call prefix
 * (dashboard.strip-prefixes, e.g. "unibank.services."). Only the matched key
 * path and URL are ever extracted - the rest of the config (secrets,
 * credentials, etc.) is never touched, so nothing sensitive can leak
 * downstream by construction.
 *
 * <p>Endpoints outside those prefixes (e.g. "unibank.components.s3.private.endpoint")
 * are infrastructure/backing-service config, not inter-API calls, and are
 * deliberately excluded so the graph only shows service-to-service traffic.
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
            } else if (value instanceof String str
                    && path.endsWith(props.getEndpointKeySuffix())
                    && hasServiceCallPrefix(path)
                    && looksLikeUrl(str)) {
                found.add(new RawEndpoint(path, str));
            }
        }
    }

    private boolean hasServiceCallPrefix(String path) {
        for (String prefix : props.getStripPrefixes()) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }
}
