package com.example.orchestrator.store.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Auto-configuration for the Engine Store module.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.example.orchestrator.store.repository")
@EntityScan(basePackages = "com.example.orchestrator.store.entity")
public class StoreAutoConfiguration {
}