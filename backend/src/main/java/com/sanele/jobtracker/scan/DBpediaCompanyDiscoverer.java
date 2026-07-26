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
 * Second live discovery source: DBpedia's SPARQL endpoint. Different dataset from
 * Wikidata, so it surfaces companies Wikidata misses. Pushes name + website onto
 * the shared {@link CompanyQueue}; the ingestor dedups and stores.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DBpediaCompanyDiscoverer {

    private final CompanyQueue queue;

    @Value("${dbpedia.enabled:true}")
    private boolean enabled;
    @Value("${dbpedia.page-size:100}")
    private int pageSize;

    private int offset = 0;
    private final RestClient http = buildClient();

    private static RestClient buildClient() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(10));
        f.setReadTimeout(Duration.ofSeconds(50));
        return RestClient.builder()
                .requestFactory(f)
                .defaultHeader("User-Agent", "sa-job-tracker/1.0 (company discoverer)")
                .defaultHeader("Accept", "application/sparql-results+json")
                .build();
    }

    private static final String SPARQL = """
            SELECT DISTINCT ?name ?homepage WHERE {
              ?c a <http://dbpedia.org/ontology/Company> ;
                 <http://dbpedia.org/ontology/locationCountry> <http://dbpedia.org/resource/South_Africa> ;
                 <http://xmlns.com/foaf/0.1/homepage> ?homepage ;
                 <http://www.w3.org/2000/01/rdf-schema#label> ?name .
              FILTER(lang(?name) = "en")
            } LIMIT %d OFFSET %d
            """;

    @Scheduled(fixedDelayString = "${dbpedia.tick-ms:15000}", initialDelay = 30000)
    public void discover() {
        if (!enabled) return;

        String query = String.format(SPARQL, pageSize, offset);
        JsonNode res;
        try {
            // Pre-built URI so RestClient does not re-encode the already-encoded query.
            java.net.URI uri = java.net.URI.create("https://dbpedia.org/sparql?format="
                    + URLEncoder.encode("application/sparql-results+json", StandardCharsets.UTF_8)
                    + "&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
            res = http.get().uri(uri).retrieve().body(JsonNode.class);
        } catch (Exception e) {
            log.warn("DBpedia discovery: query failed at offset {} ({}) — will retry", offset, e.getMessage());
            return;
        }

        JsonNode rows = res == null ? null : res.path("results").path("bindings");
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            log.info("DBpedia discovery: no more results at offset {} — looping back to start", offset);
            offset = 0;
            return;
        }

        int queued = 0;
        for (JsonNode b : rows) {
            String name = b.path("name").path("value").asText("").trim();
            String website = b.path("homepage").path("value").asText("").trim();
            if (name.isEmpty() || website.isEmpty()) continue;
            queue.offer(new CompanyQueue.Candidate(name, website, "DBpedia", null, "South Africa", "unk", null));
            queued++;
        }

        offset += pageSize;
        log.info("DBpedia discovery: fetched {} companies into the queue (offset now {}, queue size {})",
                queued, offset, queue.size());
    }
}
