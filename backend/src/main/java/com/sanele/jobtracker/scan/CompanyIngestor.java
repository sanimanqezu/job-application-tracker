package com.sanele.jobtracker.scan;

import com.sanele.jobtracker.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Consumer: every 6 seconds, pops a batch off the {@link CompanyQueue} and adds
 * the new ones to the DB, logging each. As long as the queue has fresh
 * candidates (the catalog buffers hundreds, and Wikidata refills it faster than
 * this drains), the company count rises on every beat.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyIngestor {

    private final CompanyQueue queue;
    private final CompanyRepository repo;
    private final AtomicInteger added = new AtomicInteger();

    @Value("${ingest.batch:8}")
    private int batch;

    // fixedRate = fires every tick-ms regardless of how long the last run took,
    // so the "increase every 6s" cadence holds.
    @Scheduled(fixedRateString = "${ingest.tick-ms:6000}", initialDelay = 4000)
    public void consume() {
        int addedThisTick = 0;
        for (int i = 0; i < Math.max(1, batch); i++) {
            CompanyQueue.Candidate c = queue.poll();
            if (c == null) break;                        // queue empty
            if (repo.existsByName(c.name())) continue;   // dedup — never add the same company twice
            repo.save(CompanySeeder.toEntity(c));
            int total = added.incrementAndGet();
            addedThisTick++;
            log.info("Company added #{}: {}  (queue {} waiting, {} companies in DB)",
                    total, c.name(), queue.size(), repo.count());
        }
        if (addedThisTick == 0) {
            log.info("Ingest tick: no new companies to add (queue empty) — waiting for the discoverer to refill");
        }
    }
}
