package com.message.engine.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ExecutorConfig {

    @Value("${executor.core-pool-size:10}")
    private int corePoolSize;

    @Value("${executor.max-pool-size:20}")
    private int maxPoolSize;

    @Value("${executor.queue-capacity:1000}")
    private int queueCapacity;

    @Value("${executor.thread-name-prefix:email-worker-}")
    private String threadNamePrefix;

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
