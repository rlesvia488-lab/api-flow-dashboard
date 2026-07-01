package com.socgen.unibank.apiflow.health;

import com.socgen.unibank.apiflow.config.DashboardProperties;
import com.socgen.unibank.apiflow.model.EndpointStatus;
import com.socgen.unibank.apiflow.model.HealthState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * Periodically probes every discovered endpoint URL with a lightweight GET and
 * caches the outcome. Read-only reachability probing only - no auth headers,
 * no request body, response body discarded unread.
 */
@Component
public class HealthChecker {

    private static final Logger log = LoggerFactory.getLogger(HealthChecker.class);

    private final DashboardProperties props;
    private final HttpClient httpClient;
    private final Map<String, EndpointStatus> statusByUrl = new ConcurrentHashMap<>();

    public HealthChecker(DashboardProperties props) {
        this.props = props;
        this.httpClient = buildHttpClient(props);
    }

    private static HttpClient buildHttpClient(DashboardProperties props) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(props.getHealthCheckTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL);

        if (props.isTrustAllCertsForHealthChecks()) {
            try {
                TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) { }
                    public void checkServerTrusted(X509Certificate[] chain, String authType) { }
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }};
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAll, new SecureRandom());
                builder.sslContext(sslContext);
            } catch (Exception e) {
                log.warn("Could not configure trust-all SSL context for health checks: {}", e.toString());
            }
        }

        return builder.build();
    }

    /** Kicks off (async) checks for every url not yet known; existing entries are refreshed by the scheduler. */
    public void checkAll(Set<String> urls) {
        for (String url : urls) {
            checkOne(url);
        }
    }

    public void checkOne(String url) {
        Instant start = Instant.now();
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(props.getHealthCheckTimeout())
                    .GET()
                    .build();
        } catch (Exception e) {
            statusByUrl.put(url, new EndpointStatus(url, HealthState.DOWN, null, "INVALID_URL", 0, Instant.now()));
            return;
        }

        CompletableFuture<HttpResponse<Void>> future =
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());

        future.whenComplete((response, throwable) -> {
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            if (throwable != null) {
                statusByUrl.put(url, new EndpointStatus(url, HealthState.DOWN, null, reasonFor(throwable), latencyMs, Instant.now()));
                return;
            }
            int code = response.statusCode();
            HealthState state = classify(code);
            statusByUrl.put(url, new EndpointStatus(url, state, code, null, latencyMs, Instant.now()));
        });
    }

    private static HealthState classify(int code) {
        if (code >= 200 && code < 300) {
            return HealthState.UP;
        }
        if (code == 401 || code == 403 || code == 404 || (code >= 300 && code < 400)) {
            return HealthState.DEGRADED;
        }
        return HealthState.DOWN;
    }

    private static String reasonFor(Throwable throwable) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        String name = cause.getClass().getSimpleName();
        if (cause instanceof TimeoutException || cause instanceof java.net.http.HttpTimeoutException) {
            return "TIMEOUT";
        }
        if (cause instanceof java.net.UnknownHostException) {
            return "DNS_FAIL";
        }
        if (cause instanceof java.net.ConnectException) {
            return "CONNECTION_REFUSED";
        }
        if (cause instanceof javax.net.ssl.SSLException) {
            return "TLS_ERROR";
        }
        return name;
    }

    public EndpointStatus statusFor(String url) {
        return statusByUrl.getOrDefault(url, EndpointStatus.unknown(url));
    }

    public Map<String, EndpointStatus> snapshot() {
        return Map.copyOf(statusByUrl);
    }
}
