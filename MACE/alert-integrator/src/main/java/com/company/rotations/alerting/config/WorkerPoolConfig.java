package com.company.rotations.alerting.config;

import com.company.rotations.alerting.worker.WorkerPool;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkerPoolConfig implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(WorkerPoolConfig.class);

    private final WorkerPool workerPool;

    public WorkerPoolConfig(WorkerPool workerPool) {
        this.workerPool = workerPool;
    }

    @Override
    public void run(ApplicationArguments args) {
        workerPool.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered, stopping worker pool...");
            workerPool.stop();
        }));
    }

    @PreDestroy
    public void cleanup() {
        workerPool.stop();
    }
}
