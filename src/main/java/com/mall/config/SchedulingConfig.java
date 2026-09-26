package com.mall.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "mall.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("mall-sched-");
        scheduler.setErrorHandler(t -> log.error("scheduled task failed", t));
        return scheduler;
    }
}
