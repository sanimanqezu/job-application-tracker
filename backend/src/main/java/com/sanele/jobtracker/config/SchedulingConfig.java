package com.sanele.jobtracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Multi-threaded scheduler so the different @Scheduled jobs run independently:
 * the slow Wikidata fetch and the job harvester must not block the ingestor's
 * strict 6-second beat.
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(6);   // ingestor, harvester, Wikidata, DBpedia + headroom
        scheduler.setThreadNamePrefix("sched-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
