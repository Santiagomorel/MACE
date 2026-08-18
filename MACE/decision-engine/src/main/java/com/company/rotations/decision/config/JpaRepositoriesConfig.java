package com.company.rotations.decision.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@Profile("dev")
@EntityScan(basePackages = "com.company.rotations.models")
@EnableJpaRepositories(basePackages = "com.company.rotations.decision.repository")
public class JpaRepositoriesConfig {
}
