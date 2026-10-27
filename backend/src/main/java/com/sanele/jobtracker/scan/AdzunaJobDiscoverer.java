package com.sanele.jobtracker.scan;

import com.sanele.jobtracker.entity.User;
import com.sanele.jobtracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Pulls real South-African jobs from the Adzuna aggregator on a timer and stores
 * the ones that pass the location filter. Cycles through search terms one query per
 * tick (to respect Adzuna's free-tier rate limit), advancing the page once every
 * term has been queried.
 *
 * <p>Terms span EVERY occupation category (health, finance, trades, education,
 * transport, …) so the pool isn't tech-only, and are augmented with the actual
 * desired jobs of registered users — so when a nurse or electrician signs up, the
 * harvester starts pulling their field's roles.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdzunaJobDiscoverer {

    private final AdzunaClient adzuna;
    private final JobScanService scanService;
    private final UserRepository userRepository;

    // Broad coverage across the occupation categories (SA Occupational Dictionary).
    private static final List<String> BASE_TERMS = List.of(
            // IT & engineering
            "software developer", "java developer", "data analyst", "network engineer", "it support",
            "electrical engineer", "mechanical engineer", "civil engineer",
            // healthcare
            "registered nurse", "pharmacist", "medical officer", "caregiver", "radiographer",
            "physiotherapist", "medical technologist",
            // finance & administration
            "accountant", "bookkeeper", "administrator", "receptionist", "human resources",
            "financial analyst", "office administrator", "data capturer",
            // trades & construction
            "electrician", "plumber", "carpenter", "welder", "boilermaker", "diesel mechanic",
            "artisan", "fitter and turner",
            // education & social services
            "teacher", "lecturer", "social worker", "tutor", "early childhood",
            // science & agriculture
            "laboratory technician", "agricultural", "research assistant",
            // sales, retail & hospitality
            "sales representative", "cashier", "chef", "waiter", "store manager", "call centre agent",
            "marketing", "customer service",
            // transport & logistics
            "driver", "warehouse", "logistics", "forklift operator",
            // legal, media & creative
            "paralegal", "graphic designer", "journalist", "content writer",
            // general / elementary
            "general worker", "cleaner", "security officer");

    private static final int MAX_PAGE = 3;   // pages 1..3 per term (50 results each)

    private int termIdx = 0;
    private int page = 1;

    /** Base terms plus the distinct desired-jobs of registered users (deduped, capped). */
    private List<String> terms() {
        LinkedHashSet<String> all = new LinkedHashSet<>(BASE_TERMS);
        userRepository.findAll().stream()
                .map(User::getDesiredJob)
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toLowerCase())
                .forEach(all::add);
        return new ArrayList<>(all);
    }

    @Scheduled(fixedDelayString = "${adzuna.tick-ms:90000}", initialDelay = 25000)
    public void discover() {
        if (!adzuna.isConfigured()) return;   // no key → silently skip

        List<String> terms = terms();
        if (terms.isEmpty()) return;
        String term = terms.get(termIdx % terms.size());
        List<JobScanService.SourcedJob> jobs = adzuna.search(term, page);
        int added = scanService.storeExternalJobs(jobs, "adzuna");
        log.info("Adzuna['{}' p{}]: {} listings fetched, {} new SA jobs stored", term, page, jobs.size(), added);

        // advance: next term; once all terms queried at this page, move to next page
        termIdx++;
        if (termIdx % terms.size() == 0) {
            page = page >= MAX_PAGE ? 1 : page + 1;
        }
    }
}
