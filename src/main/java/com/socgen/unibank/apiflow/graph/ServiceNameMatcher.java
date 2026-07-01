package com.socgen.unibank.apiflow.graph;

import com.socgen.unibank.apiflow.config.DashboardProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Bridges the gap between a config key like
 * "unibank.services.accounts.internal.endpoint" (dotted, prefixed, suffixed)
 * and a Vault service folder name like "accounts-service" (kebab-case, suffixed).
 * Both sides are normalized to a dotted "match key" and compared by longest
 * shared dotted prefix, since exact equality rarely holds in practice.
 */
@Component
public class ServiceNameMatcher {

    private final DashboardProperties props;

    public ServiceNameMatcher(DashboardProperties props) {
        this.props = props;
    }

    /** e.g. "accounts-service" -> "accounts", "account-identity-service" -> "account.identity" */
    public String normalizeServiceName(String serviceName) {
        String s = serviceName.toLowerCase();
        for (String suffix : props.getServiceNameSuffixes()) {
            String lower = suffix.toLowerCase();
            if (s.endsWith(lower)) {
                s = s.substring(0, s.length() - lower.length());
                break;
            }
        }
        return s.replace('-', '.').replace('_', '.');
    }

    /** e.g. "unibank.services.accounts.internal.endpoint" -> "accounts.internal" */
    public String extractTargetKey(String keyPath) {
        String s = keyPath;
        if (s.endsWith(props.getEndpointKeySuffix())) {
            s = s.substring(0, s.length() - props.getEndpointKeySuffix().length());
        }
        for (String prefix : props.getStripPrefixes()) {
            if (s.startsWith(prefix)) {
                s = s.substring(prefix.length());
                break;
            }
        }
        return s;
    }

    /**
     * Best-matching original service name for a normalized target, or null if
     * nothing lines up (caller should render it as an external/unresolved node).
     * normalizedToOriginal maps normalizeServiceName(x) -> x for every known service.
     */
    public String matchService(String target, Map<String, String> normalizedToOriginal) {
        String direct = normalizedToOriginal.get(target);
        if (direct != null) {
            return direct;
        }

        String best = null;
        int bestScore = 0;
        for (Map.Entry<String, String> candidate : normalizedToOriginal.entrySet()) {
            String norm = candidate.getKey();
            if (norm.isEmpty()) {
                continue;
            }
            boolean prefixMatch = target.startsWith(norm + ".") || norm.startsWith(target + ".");
            if (prefixMatch) {
                int score = Math.min(norm.length(), target.length());
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate.getValue();
                }
            }
        }
        return best;
    }
}
