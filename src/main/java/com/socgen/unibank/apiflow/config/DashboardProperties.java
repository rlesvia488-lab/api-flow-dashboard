package com.socgen.unibank.apiflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component("dashboardProperties")
@ConfigurationProperties(prefix = "dashboard")
public class DashboardProperties {

    /** Suffix identifying an outbound-call property, e.g. "unibank.services.accounts.internal.endpoint". */
    private String endpointKeySuffix = ".endpoint";

    /** Prefixes stripped from an endpoint key before matching it to a service name. */
    private List<String> stripPrefixes = List.of("unibank.services.", "unibank.service.");

    /** Suffixes stripped from a Vault service folder name when normalizing it for matching. */
    private List<String> serviceNameSuffixes = List.of("-service", "_service", ".service");

    private Duration configRefreshInterval = Duration.ofSeconds(60);

    private Duration healthCheckInterval = Duration.ofSeconds(20);

    private Duration healthCheckTimeout = Duration.ofSeconds(3);

    /** Health checks are read-only reachability probes; internal PRD certs are often self-signed. */
    private boolean trustAllCertsForHealthChecks = true;

    public String getEndpointKeySuffix() { return endpointKeySuffix; }
    public void setEndpointKeySuffix(String endpointKeySuffix) { this.endpointKeySuffix = endpointKeySuffix; }

    public List<String> getStripPrefixes() { return stripPrefixes; }
    public void setStripPrefixes(List<String> stripPrefixes) { this.stripPrefixes = stripPrefixes; }

    public List<String> getServiceNameSuffixes() { return serviceNameSuffixes; }
    public void setServiceNameSuffixes(List<String> serviceNameSuffixes) { this.serviceNameSuffixes = serviceNameSuffixes; }

    public Duration getConfigRefreshInterval() { return configRefreshInterval; }
    public void setConfigRefreshInterval(Duration configRefreshInterval) { this.configRefreshInterval = configRefreshInterval; }

    public Duration getHealthCheckInterval() { return healthCheckInterval; }
    public void setHealthCheckInterval(Duration healthCheckInterval) { this.healthCheckInterval = healthCheckInterval; }

    public Duration getHealthCheckTimeout() { return healthCheckTimeout; }
    public void setHealthCheckTimeout(Duration healthCheckTimeout) { this.healthCheckTimeout = healthCheckTimeout; }

    public boolean isTrustAllCertsForHealthChecks() { return trustAllCertsForHealthChecks; }
    public void setTrustAllCertsForHealthChecks(boolean trustAllCertsForHealthChecks) { this.trustAllCertsForHealthChecks = trustAllCertsForHealthChecks; }
}
