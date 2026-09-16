package com.possaas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages = "com.possaas")
@EntityScan(basePackages = "com.possaas")
@EnableJpaRepositories(basePackages = "com.possaas")
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@EnableTransactionManagement
@EnableCaching
@EnableScheduling
@EnableAsync
public class PosSaasApplication {

    public static void main(String[] args) {
        SpringApplication.run(PosSaasApplication.class, args);
    }
}
