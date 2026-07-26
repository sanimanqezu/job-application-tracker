package com.sanele.jobtracker.scan;

import com.sanele.jobtracker.catalog.Company;
import com.sanele.jobtracker.catalog.CompanyCatalog;
import com.sanele.jobtracker.entity.CompanyEntity;
import com.sanele.jobtracker.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Producer: on startup, pushes every bundled-catalog company that isn't already
 * in the DB onto the {@link CompanyQueue}. The ingestor then drains it a batch
 * at a time so the count climbs on a steady beat.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompanySeeder {

    private final CompanyCatalog catalog;
    private final CompanyRepository repo;
    private final CompanyQueue queue;

    @EventListener(ApplicationReadyEvent.class)
    public void enqueueCatalog() {
        Set<String> inDb = repo.findAll().stream()
                .map(c -> c.getName().toLowerCase())
                .collect(Collectors.toSet());
        int queued = 0;
        for (Company c : catalog.all()) {
            if (c.name() == null || c.name().isBlank() || inDb.contains(c.name().toLowerCase())) continue;
            queue.offer(new CompanyQueue.Candidate(c.name(), c.apply(), c.segment(),
                    c.stack(), c.city(), c.fit(), c.note()));
            queued++;
        }
        log.info("Catalog seeded into queue: {} companies queued to add ({} already in DB)", queued, inDb.size());
    }

    /** Build a DB row from a queued candidate. */
    static CompanyEntity toEntity(CompanyQueue.Candidate c) {
        return CompanyEntity.builder()
                .name(trim(c.name(), 200))
                .applyUrl(trim(c.applyUrl(), 600))
                .segment(trim(c.segment(), 80))
                .stack(trim(c.stack(), 200))
                .city(trim(c.city(), 120))
                .fit(trim(c.fit(), 20))
                .note(trim(c.note(), 500))
                .build();
    }

    static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
