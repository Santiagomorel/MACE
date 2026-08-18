package com.company.rotations.verification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.company.rotations.verification",
        "com.company.rotations.logging"
})
@EntityScan(basePackages = {"com.company.rotations.models", "com.company.rotations.verification.model"})
@EnableJpaRepositories(basePackages = "com.company.rotations.verification.repository")
public class VerificationEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(VerificationEngineApplication.class, args);
    }
}
