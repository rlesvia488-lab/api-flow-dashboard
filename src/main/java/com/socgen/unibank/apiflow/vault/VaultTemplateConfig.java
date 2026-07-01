package com.socgen.unibank.apiflow.vault;

import com.socgen.unibank.apiflow.config.VaultProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.SimpleSessionManager;
import org.springframework.vault.client.ClientHttpRequestFactoryFactory;
import org.springframework.vault.client.RestTemplateBuilder;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.web.client.RestTemplate;

/**
 * Builds a standalone {@link VaultTemplate} authenticated via AppRole, reusing the
 * VAULT_URI / VAULT_NAMESPACE / VAULT_ROLE_ID / VAULT_SECRET_ID env vars the platform
 * already exports. We use spring-vault-core directly (not spring-cloud-vault) because
 * this app needs ad-hoc LIST/READ across many service paths, not a single bootstrap
 * PropertySource bound to its own config.
 */
@Configuration
@ConditionalOnProperty(prefix = "vault", name = "enabled", havingValue = "true")
public class VaultTemplateConfig {

    @Bean
    public VaultEndpoint vaultEndpoint(VaultProperties props) {
        return VaultEndpoint.from(java.net.URI.create(props.getUri()));
    }

    @Bean
    public ClientHttpRequestFactory vaultRequestFactory() {
        return ClientHttpRequestFactoryFactory.create(new ClientOptions(), SslConfiguration.unconfigured());
    }

    private static void applyNamespace(RestTemplate restTemplate, String namespace) {
        if (StringUtils.hasText(namespace)) {
            restTemplate.getInterceptors().add(VaultClients.createNamespaceInterceptor(namespace));
        }
    }

    @Bean
    public AppRoleAuthentication clientAuthentication(VaultEndpoint endpoint,
                                                       ClientHttpRequestFactory requestFactory,
                                                       VaultProperties props) {
        RestTemplate authRestTemplate = VaultClients.createRestTemplate(endpoint, requestFactory);
        applyNamespace(authRestTemplate, props.getNamespace());

        AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
                .roleId(AppRoleAuthenticationOptions.RoleId.provided(props.getRoleId()))
                .secretId(AppRoleAuthenticationOptions.SecretId.provided(props.getSecretId()))
                .build();
        return new AppRoleAuthentication(options, authRestTemplate);
    }

    @Bean
    public VaultTemplate vaultTemplate(VaultEndpoint endpoint,
                                        ClientHttpRequestFactory requestFactory,
                                        AppRoleAuthentication clientAuthentication,
                                        VaultProperties props) {
        RestTemplateBuilder builder = RestTemplateBuilder.builder()
                .endpoint(endpoint)
                .requestFactory(requestFactory)
                .customizers(restTemplate -> applyNamespace(restTemplate, props.getNamespace()));

        return new VaultTemplate(builder, new SimpleSessionManager(clientAuthentication));
    }
}
