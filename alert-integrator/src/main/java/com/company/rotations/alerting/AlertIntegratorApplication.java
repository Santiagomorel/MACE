package com.company.rotations.alerting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(excludeName = {
        "com.company.rotations.logging.config.LoggingAutoConfiguration"
})
@ComponentScan(basePackages = {
        "com.company.rotations.alerting",
        "com.company.rotations.logging"
})
@EntityScan(basePackages = {
        "com.company.rotations.models",
        "com.company.rotations.alerting.dlq",
        "com.company.rotations.logging"
})
@EnableScheduling
public class AlertIntegratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertIntegratorApplication.class, args);
    }
}
