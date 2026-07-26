package com.sanele.jobtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JobTrackerApplication {

    public static void main(String[] args) {
        // Bring up the Postgres container for this run (and stop it on exit) so a
        // fresh clone needs no manual DB setup. Skips itself if Postgres is already
        // running or JOBTRACKER_AUTO_DB=false.
        DockerBootstrap.ensureDatabaseUp();
        SpringApplication.run(JobTrackerApplication.class, args);
    }
}
