package com.company.rotations.alerting.config;

import com.company.rotations.alerting.worker.WorkerPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkerPoolConfigTest {

    private WorkerPool workerPool;
    private WorkerPoolConfig config;

    @BeforeEach
    void setUp() {
        workerPool = mock(WorkerPool.class);
        config = new WorkerPoolConfig(workerPool);
    }

    @Test
    @DisplayName("Should start worker pool on run")
    void shouldStartWorkerPool() {
        ApplicationArguments args = mock(ApplicationArguments.class);
        config.run(args);

        verify(workerPool).start();
    }

    @Test
    @DisplayName("Should stop worker pool on cleanup")
    void shouldStopOnCleanup() {
        config.cleanup();
        verify(workerPool).stop();
    }

    @Test
    @DisplayName("Should be a Spring Configuration")
    void shouldBeConfiguration() {
        boolean hasConfig = false;
        for (java.lang.annotation.Annotation a : WorkerPoolConfig.class.getAnnotations()) {
            if (a.annotationType().getName().contains("Configuration")) {
                hasConfig = true;
                break;
            }
        }
        assertTrue(hasConfig);
    }

    @Test
    @DisplayName("Should implement ApplicationRunner")
    void shouldImplementApplicationRunner() {
        assertTrue(org.springframework.boot.ApplicationRunner.class.isAssignableFrom(
                WorkerPoolConfig.class));
    }
}
