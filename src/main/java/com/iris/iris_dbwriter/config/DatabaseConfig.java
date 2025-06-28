package com.iris.iris_dbwriter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@Configuration
@EnableTransactionManagement
@EntityScan(basePackages = "com.iris.common.model.db")
@EnableJpaRepositories(basePackages = "com.iris.iris_dbwriter.repository")
public class DatabaseConfig {
    // The default configuration from Spring Boot auto-configuration is sufficient
    // We just need to enable the correct packages for scanning
}