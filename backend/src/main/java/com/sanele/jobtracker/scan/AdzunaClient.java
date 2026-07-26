package com.sanele.jobtracker.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanele.jobtracker.ats.RawJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads South-African job listings from the Adzuna aggregator API
 * (https://developer.adzuna.com/). Adzuna indexes jobs from employers that have
 * no ATS API of their own — banks, universities, retailers — so this is how we
 * surface roles from companies we otherwise can't scrape. Each result carries a
 * {@code redirect_url} that lands on the original apply page.
 *
 * <p>Keys come from config ({@code adzuna.app-id}/{@code adzuna.app-key}), which
 * are loaded from the gitignored {@code secrets.properties} — never hard-coded.
 */
@Slf4j
@Component
public class AdzunaClient {

    @Value("${adzuna.enabled:true}")
    private boolean enabled;
    @Value("${adzuna.app-id:}")
    private String appId;
    @Value("${adzuna.app-key:}")
    private String appKey;
    @Value("${adzuna.country:za}")
    private String country;

    private final RestClient http = build();

    private static RestClient build() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(8));
        f.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().requestFactory(f)
                .defaultHeader("User-Agent", "sa-job-tracker/1.0")
                .build();
    }

    /** True only when enabled and both keys are present. */
    public boolean isConfigured() {
        return enabled && appId != null && !appId.isBlank() && appKey != null && !appKey.isBlank();
    }

    /**
     * Search page {@code page} for {@code what}, returning up to 50 SA jobs mapped
     * to the shared pipeline type. Never throws — returns empty on any error.
     */
    public List<JobScanService.SourcedJob> search(String what, int page) {
        if (!isConfigured()) return List.of();
        try {
            // Pre-build the URI so RestClient does NOT re-encode the query string.
            String url = "https://api.adzuna.com/v1/api/jobs/" + country + "/search/" + Math.max(1, page)
                    + "?app_id=" + enc(appId)
                    + "&app_key=" + enc(appKey)
                    + "&results_per_page=50"
                    + "&what=" + enc(what)
                    + "&content-type=application/json";
            JsonNode d = http.get().uri(URI.create(url)).retrieve().body(JsonNode.class);
            if (d == null) return List.of();

            List<JobScanService.SourcedJob> out = new ArrayList<>();
            for (JsonNode r : d.path("results")) {
                String title = stripTags(text(r, "title"));
                String redirect = text(r, "redirect_url");
                if (title.isBlank() || redirect.isBlank()) continue;
                String company = text(r.path("company"), "display_name");
                String location = text(r.path("location"), "display_name");
                String desc = stripTags(text(r, "description"));
                RawJob job = new RawJob(title, location, redirect, parseDate(text(r, "created")), desc);
                out.add(new JobScanService.SourcedJob(
                        company.isBlank() ? "Adzuna listing" : company, "Adzuna", location, job));
            }
            return out;
        } catch (Exception e) {
            log.warn("Adzuna search failed for '{}' p{}: {}", what, page, e.getMessage());
            return List.of();
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText("");
    }

    private static String stripTags(String s) {
        if (s == null || s.isBlank()) return "";
        return s.replaceAll("(?s)<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private static LocalDateTime parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return OffsetDateTime.parse(s).toLocalDateTime();
        } catch (Exception e) {
            return null;
        }
    }
}
