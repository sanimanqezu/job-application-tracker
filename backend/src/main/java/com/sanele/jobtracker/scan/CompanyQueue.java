package com.sanele.jobtracker.scan;

import org.springframework.stereotype.Component;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Shared buffer of companies waiting to be added to the DB. Producers (the
 * bundled catalog seeder and the Wikidata discoverer) push candidates in; the
 * ingestor pops a batch every 6 seconds. Keeping producers and the consumer
 * decoupled is what lets the count rise on a steady beat.
 */
@Component
public class CompanyQueue {

    public record Candidate(String name, String applyUrl, String segment,
                            String stack, String city, String fit, String note) {}

    private final Queue<Candidate> queue = new ConcurrentLinkedQueue<>();

    public void offer(Candidate c) {
        if (c != null && c.name() != null && !c.name().isBlank()) queue.offer(c);
    }

    public Candidate poll() {
        return queue.poll();
    }

    public int size() {
        return queue.size();
    }
}
