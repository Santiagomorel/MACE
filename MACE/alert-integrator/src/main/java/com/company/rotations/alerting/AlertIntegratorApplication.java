package com.company.rotations.alerting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.company.rotations.alerting",
        "com.company.rotations.logging"
})
@EntityScan(basePackages = {
        "com.company.rotations.models",
        "com.company.rotations.alerting.dlq"
})
@EnableJpaRepositories(basePackages = {
        "com.company.rotations.alerting.repository",
        "com.company.rotations.alerting.dlq"
})
@EnableScheduling
public class AlertIntegratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertIntegratorApplication.class, args);
    }
}
