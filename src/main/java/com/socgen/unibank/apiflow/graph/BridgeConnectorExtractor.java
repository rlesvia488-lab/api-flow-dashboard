package com.socgen.unibank.apiflow.graph;

import com.socgen.unibank.apiflow.config.DashboardProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Extracts per-country partner endpoints from bridge/connector config blocks,
 * e.g.:
 *
 * <pre>
 * "unibank.bridge.connectors.amplitude_v11": {
 *   "BF": { "url": "https://aif.gslb.com:443/burp", "userCode": "..." },
 *   "INTEROP_GIMAC": {
 *     "CM": { "url": "https://aif.gslb.com:443/camp/", "userCode": "..." }
 *   },
 *   "defaults": { "delayInMillis": "100", ... }
 * }
 * </pre>
 *
 * Unlike {@link EndpointExtractor}, the target here is never resolved against
 * known services - each country entry is deliberately graphed as its own
 * "country" node, since these are external partner endpoints, not other
 * Vault-managed APIs. The "defaults" bag (retry/delay tuning, not an
 * endpoint) is skipped; a nested group like "INTEROP_GIMAC" is unwrapped one
 * level so its country entries are still picked up.
 */
@Component
public class BridgeConnectorExtractor {

    private final DashboardProperties props;

    public BridgeConnectorExtractor(DashboardProperties props) {
        this.props = props;
    }

    public record CountryEndpoint(String keyPath, String country, String connector, String url) {
    }

    public List<CountryEndpoint> extract(Map<String, Object> config) {
        List<CountryEndpoint> found = new ArrayList<>();
        String prefix = props.getBridgeConnectorPrefix();

        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix) && entry.getValue() instanceof Map<?, ?> connectorMap) {
                String connectorName = key.substring(prefix.length());
                walk(key, connectorName, asStringMap(connectorMap), found);
            }
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private void walk(String basePath, String connector, Map<String, Object> node, List<CountryEndpoint> found) {
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            String countryOrGroup = entry.getKey();
            if (countryOrGroup.equalsIgnoreCase("defaults")) {
                continue;
            }
            if (!(entry.getValue() instanceof Map<?, ?> valueMapRaw)) {
                continue;
            }
            Map<String, Object> valueMap = asStringMap(valueMapRaw);
            String path = basePath + "." + countryOrGroup;
            Object url = valueMap.get("url");

            if (url instanceof String urlStr && looksLikeUrl(urlStr) && !isExcludedUrl(urlStr)) {
                found.add(new CountryEndpoint(path + ".url", countryOrGroup.toUpperCase(java.util.Locale.ROOT), connector, urlStr));
            } else if (url == null) {
                // Not a leaf {url, ...} entry - a nested group (e.g. INTEROP_GIMAC); unwrap one level.
                walk(path, connector, valueMap, found);
            }
        }
    }

    private boolean looksLikeUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private boolean isExcludedUrl(String url) {
        for (String excludedPrefix : props.getExcludedUrlPrefixes()) {
            if (url.regionMatches(true, 0, excludedPrefix, 0, excludedPrefix.length())) {
                return true;
            }
        }
        return false;
    }
}
