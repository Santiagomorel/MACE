package com.company.rotations.decision;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.company.rotations.decision",
        "com.company.rotations.logging"
})
@EnableScheduling
public class DecisionEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(DecisionEngineApplication.class, args);
    }
}
