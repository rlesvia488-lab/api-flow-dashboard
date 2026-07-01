package com.socgen.unibank.apiflow.vault;

import com.socgen.unibank.apiflow.config.VaultProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.Versioned;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "vault", name = "enabled", havingValue = "true")
public class VaultServiceCatalog implements ServiceCatalog {

    private static final Logger log = LoggerFactory.getLogger(VaultServiceCatalog.class);

    private final VaultTemplate vaultTemplate;
    private final VaultProperties props;

    public VaultServiceCatalog(VaultTemplate vaultTemplate, VaultProperties props) {
        this.vaultTemplate = vaultTemplate;
        this.props = props;
    }

    @Override
    public List<String> listServiceNames() {
        try {
            List<String> children = vaultTemplate.list(props.getKvMount() + "/metadata");
            if (children == null) {
                return Collections.emptyList();
            }
            return children.stream()
                    .map(name -> name.endsWith("/") ? name.substring(0, name.length() - 1) : name)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to LIST services under {}/metadata: {}", props.getKvMount(), e.toString());
            return Collections.emptyList();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> readConfig(String serviceName) {
        String path = serviceName + "/" + props.getEnv() + "/" + props.getSecretName();
        try {
            Versioned<Map<String, Object>> versioned = vaultTemplate
                    .opsForVersionedKeyValue(props.getKvMount())
                    .get(path, (Class<Map<String, Object>>) (Class<?>) Map.class);
            if (versioned == null || versioned.getData() == null) {
                return Collections.emptyMap();
            }
            return versioned.getData();
        } catch (Exception e) {
            log.warn("Failed to read config for service '{}' at {}/data/{}: {}",
                    serviceName, props.getKvMount(), path, e.toString());
            return Collections.emptyMap();
        }
    }
}
