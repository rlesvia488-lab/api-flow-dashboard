package com.socgen.unibank.apiflow.vault;

import java.util.List;
import java.util.Map;

/**
 * Abstraction over "where do service configs live". Real implementation talks to
 * Vault; a demo implementation serves canned data so the dashboard is runnable
 * without live Vault credentials.
 */
public interface ServiceCatalog {

    List<String> listServiceNames();

    /** Raw config map for the given service, or empty map if not found/unreadable. */
    Map<String, Object> readConfig(String serviceName);
}
