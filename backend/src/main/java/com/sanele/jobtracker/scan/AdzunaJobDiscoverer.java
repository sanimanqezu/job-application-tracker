package com.sanele.jobtracker.scan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pulls real South-African jobs from the Adzuna aggregator on a timer and stores
 * the ones that pass the CV / location / seniority filters. Cycles through a set
 * of developer search terms, one query per tick (to respect Adzuna's free-tier
 * rate limit), advancing the page once every term has been queried.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdzunaJobDiscoverer {

    private final AdzunaClient adzuna;
    private final JobScanService scanService;

    // Terms tuned to the CV: junior-friendly developer roles across the stack.
    private static final List<String> TERMS = List.of(
            "junior software developer", "graduate developer", "software developer",
            "java developer", "spring boot developer", "react developer", "next.js developer",
            "full stack developer", "backend developer", "frontend developer",
            "software engineer", "python developer", "c# developer", ".net developer",
            "typescript developer", "node developer", "web developer", "qa automation");

    private static final int MAX_PAGE = 5;   // pages 1..5 per term (50 results each)

    private int termIdx = 0;
    private int page = 1;

    @Scheduled(fixedDelayString = "${adzuna.tick-ms:90000}", initialDelay = 25000)
    public void discover() {
        if (!adzuna.isConfigured()) return;   // no key → silently skip

        String term = TERMS.get(termIdx % TERMS.size());
        List<JobScanService.SourcedJob> jobs = adzuna.search(term, page);
        int added = scanService.storeExternalJobs(jobs, "adzuna");
        log.info("Adzuna['{}' p{}]: {} listings fetched, {} new SA jobs stored", term, page, jobs.size(), added);

        // advance: next term; once all terms queried at this page, move to next page
        termIdx++;
        if (termIdx % TERMS.size() == 0) {
            page = page >= MAX_PAGE ? 1 : page + 1;
        }
    }
}
