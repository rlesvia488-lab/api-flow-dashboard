package com.socgen.unibank.apiflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Bound from the same env vars the platform's launch script already exports
 * (VAULT_URI, VAULT_NAMESPACE, VAULT_ROLE_ID, VAULT_SECRET_ID, VAULT_ENABLED)
 * via Spring Boot's relaxed env-var binding, so no run-script changes are needed.
 */
@Component("vaultProperties")
@ConfigurationProperties(prefix = "vault")
public class VaultProperties {

    /** e.g. https://vault.example.internal:8200 */
    private String uri;

    /** Vault Enterprise namespace header, e.g. myVault/abb-namespace. Optional. */
    private String namespace;

    private String roleId;

    private String secretId;

    /** When false (default), the app runs in demo mode with synthetic services instead of calling Vault. */
    private boolean enabled = false;

    /** KV v2 secrets engine (mount) name, e.g. "secret". Not the trigram - see {@link #trigram}. */
    private String kvMount = "secret";

    /** Path prefix inside the mount identifying your org/BU, e.g. "abb": secret/data/abb/<service>/<env>/default */
    private String trigram = "abb";

    /** Environment path segment: <trigram>/<service>/<env>/default */
    private String env = "prd";

    /** Leaf secret name under each service/env path. */
    private String secretName = "default";

    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }

    public String getSecretId() { return secretId; }
    public void setSecretId(String secretId) { this.secretId = secretId; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getKvMount() { return kvMount; }
    public void setKvMount(String kvMount) { this.kvMount = kvMount; }

    public String getTrigram() { return trigram; }
    public void setTrigram(String trigram) { this.trigram = trigram; }

    public String getEnv() { return env; }
    public void setEnv(String env) { this.env = env; }

    public String getSecretName() { return secretName; }
    public void setSecretName(String secretName) { this.secretName = secretName; }
}
