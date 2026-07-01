package com.socgen.unibank.apiflow.scheduler;

import com.socgen.unibank.apiflow.graph.Graph;
import com.socgen.unibank.apiflow.graph.GraphBuilder;
import com.socgen.unibank.apiflow.graph.GraphCache;
import com.socgen.unibank.apiflow.health.HealthChecker;
import com.socgen.unibank.apiflow.vault.ServiceCatalog;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GraphRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(GraphRefreshScheduler.class);

    private final GraphBuilder graphBuilder;
    private final GraphCache graphCache;
    private final HealthChecker healthChecker;
    private final ServiceCatalog serviceCatalog;

    public GraphRefreshScheduler(GraphBuilder graphBuilder, GraphCache graphCache,
                                  HealthChecker healthChecker, ServiceCatalog serviceCatalog) {
        this.graphBuilder = graphBuilder;
        this.graphCache = graphCache;
        this.healthChecker = healthChecker;
        this.serviceCatalog = serviceCatalog;
    }

    @PostConstruct
    public void initialRefresh() {
        refreshGraph();
    }

    @Scheduled(fixedDelayString = "#{dashboardProperties.configRefreshInterval.toMillis()}")
    public void refreshGraph() {
        try {
            Graph graph = graphBuilder.build(serviceCatalog);
            graphCache.set(graph);
            log.info("Graph refreshed: {} services, {} endpoint edges", graph.nodes().size(), graph.edges().size());
        } catch (Exception e) {
            log.warn("Graph refresh failed, keeping previous snapshot: {}", e.toString());
        }
    }

    @Scheduled(fixedDelayString = "#{dashboardProperties.healthCheckInterval.toMillis()}")
    public void refreshHealth() {
        healthChecker.checkAll(graphCache.get().urls());
    }
}
