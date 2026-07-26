package com.sanele.jobtracker.scan;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * A live company-discovery API: pages through South-African organizations on
 * Wikidata's public SPARQL endpoint and adds any it hasn't seen before. This is
 * genuine discovery of NEW companies from an external source — not a fixed list —
 * so the company count keeps climbing until the source is exhausted, then loops.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WikidataCompanyDiscoverer {

    private final CompanyQueue queue;

    @Value("${wikidata.enabled:true}")
    private boolean enabled;
    @Value("${wikidata.page-size:40}")
    private int pageSize;

    private int offset = 0;
    private final RestClient http = buildClient();

    private static RestClient buildClient() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(10));
        f.setReadTimeout(Duration.ofSeconds(50));
        return RestClient.builder()
                .requestFactory(f)
                // Wikidata requires a descriptive User-Agent
                .defaultHeader("User-Agent", "sa-job-tracker/1.0 (company discoverer)")
                .defaultHeader("Accept", "application/sparql-results+json")
                .build();
    }

    // Businesses/enterprises located in South Africa that have an official website.
    // Direct P31 across many company/org types (no slow subclass path) located in
    // South Africa with an official website. Covers enterprises, public companies,
    // banks, insurers, retailers, employment/recruitment agencies (Q1786993),
    // nonprofits, universities and government agencies. No ORDER BY (it times out).
    private static final String SPARQL = """
            SELECT DISTINCT ?itemLabel ?website WHERE {
              VALUES ?type {
                wd:Q4830453 wd:Q891723 wd:Q6881511 wd:Q783794 wd:Q22687
                wd:Q2001305 wd:Q4287745 wd:Q1786993 wd:Q163740 wd:Q3918 wd:Q327333
              }
              ?item wdt:P31 ?type ; wdt:P17 wd:Q258 ; wdt:P856 ?website .
              SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
            } LIMIT %d OFFSET %d
            """;

    @Scheduled(fixedDelayString = "${wikidata.tick-ms:8000}", initialDelay = 20000)
    public void discover() {
        if (!enabled) return;

        String query = String.format(SPARQL, pageSize, offset);
        JsonNode res;
        try {
            // Pre-built URI so RestClient does NOT re-encode the already-encoded query
            // (passing a String would double-encode '?' → '%3F' and Wikidata 400s).
            java.net.URI uri = java.net.URI.create("https://query.wikidata.org/sparql?format=json&query="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8));
            res = http.get().uri(uri).retrieve().body(JsonNode.class);
        } catch (Exception e) {
            log.warn("Wikidata discovery: query failed at offset {} ({}) — will retry", offset, e.getMessage());
            return;
        }

        JsonNode rows = res == null ? null : res.path("results").path("bindings");
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            log.info("Wikidata discovery: no more results at offset {} — looping back to start", offset);
            offset = 0;
            return;
        }

        int queued = 0;
        for (JsonNode b : rows) {
            String name = b.path("itemLabel").path("value").asText("").trim();
            String website = b.path("website").path("value").asText("").trim();
            // skip unlabeled items (label falls back to the Q-id) and empties
            if (name.isEmpty() || website.isEmpty() || name.matches("Q\\d+")) continue;
            queue.offer(new CompanyQueue.Candidate(name, website, "Wikidata", null, "South Africa", "unk", null));
            queued++;
        }

        offset += pageSize;
        log.info("Wikidata discovery: fetched {} companies into the queue (offset now {}, queue size {})",
                queued, offset, queue.size());
    }
}
