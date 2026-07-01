package com.socgen.unibank.apiflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ApiFlowDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiFlowDashboardApplication.class, args);
    }
}
