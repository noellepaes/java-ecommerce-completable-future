package com.ecommerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Pool dedicado para I/O concorrente via {@link java.util.concurrent.CompletableFuture}.
 * <p>
 * Em Spring MVC + JDBC/Redis bloqueante, este pool NÃO elimina o bloqueio:
 * só libera a thread do Tomcat de esperar várias chamadas sequenciais e permite
 * que tarefas <em>independentes</em> avancem em paralelo ({@code allOf} / {@code thenCombine}).
 */
@Configuration
public class AsyncExecutorConfig {

    public static final String IO_TASK_EXECUTOR = "ioTaskExecutor";

    @Bean(name = IO_TASK_EXECUTOR)
    public Executor ioTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("io-async-");
        executor.setCorePoolSize(16);
        executor.setMaxPoolSize(64);
        executor.setQueueCapacity(500);
        executor.setAllowCoreThreadTimeOut(true);
        executor.initialize();
        return executor;
    }
}
