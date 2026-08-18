package com.company.rotations.actionexecutor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.company.rotations.actionexecutor",
        "com.company.rotations.logging"
})
@EntityScan(basePackages = "com.company.rotations.models")
@EnableJpaRepositories(basePackages = "com.company.rotations.actionexecutor.repository")
public class ActionExecutorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActionExecutorApplication.class, args);
    }
}
