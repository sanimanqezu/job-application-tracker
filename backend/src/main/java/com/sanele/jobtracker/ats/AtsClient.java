package com.sanele.jobtracker.ats;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanele.jobtracker.catalog.Company;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talks to the public JSON job-board APIs of the major applicant-tracking
 * systems, and auto-discovers which one a company uses — either by reading the
 * ATS out of its careers URL, or (deep probe) by guessing tokens from its
 * name/domain and trying each provider.
 */
@Slf4j
@Component
public class AtsClient {

    /** A board that returned openings for a company. */
    public record Board(String source, String type, String token, List<RawJob> jobs) {}

    private final RestClient http;

    public AtsClient() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(8));
        f.setReadTimeout(Duration.ofSeconds(14));
        this.http = RestClient.builder().requestFactory(f).build();
    }

    private static final List<String> PROBE_PROVIDERS =
            List.of("greenhouse", "lever", "recruitee", "smartrecruiters", "workable", "ashby", "breezy", "personio", "bamboohr");

    // ---- discovery ------------------------------------------------------

    public List<Board> discover(Company company, boolean probe) {
        List<Board> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // (a) detect the ATS straight from the careers URL
        detectFromUrl(company.apply()).ifPresent(d -> addBoard(found, seen, d[0], d[1], d[0] + " (from url)"));

        if (!probe) return found;

        // (b) probe guessed tokens across providers (stop at first hit per provider)
        List<String> tokens = candidateTokens(company);
        for (String type : PROBE_PROVIDERS) {
            boolean already = found.stream().anyMatch(b -> b.type().equals(type));
            if (already) continue;
            for (String token : tokens) {
                List<RawJob> jobs = fetch(type, token);
                if (!jobs.isEmpty() && seen.add(type + ":" + token)) {
                    found.add(new Board(type + ":" + token + " (probed)", type, token, jobs));
                    break;
                }
            }
        }
        return found;
    }

    private void addBoard(List<Board> found, Set<String> seen, String type, String token, String label) {
        String key = type + ":" + token;
        if (!seen.add(key)) return;
        List<RawJob> jobs = fetch(type, token);
        if (!jobs.isEmpty()) found.add(new Board(label, type, token, jobs));
    }

    private static final List<Object[]> URL_PATTERNS = List.of(
            new Object[]{Pattern.compile("(?:job-boards?|boards(?:-api)?)\\.greenhouse\\.io/([a-z0-9_-]+)", Pattern.CASE_INSENSITIVE), "greenhouse"},
            new Object[]{Pattern.compile("jobs\\.(?:eu\\.)?lever\\.co/([a-z0-9_-]+)", Pattern.CASE_INSENSITIVE), "lever"},
            new Object[]{Pattern.compile("([a-z0-9_-]+)\\.recruitee\\.com", Pattern.CASE_INSENSITIVE), "recruitee"},
            new Object[]{Pattern.compile("(?:careers|jobs)\\.smartrecruiters\\.com/([a-z0-9_-]+)", Pattern.CASE_INSENSITIVE), "smartrecruiters"},
            new Object[]{Pattern.compile("apply\\.workable\\.com/([a-z0-9_-]+)", Pattern.CASE_INSENSITIVE), "workable"},
            new Object[]{Pattern.compile("jobs\\.ashbyhq\\.com/([a-z0-9_-]+)", Pattern.CASE_INSENSITIVE), "ashby"},
            new Object[]{Pattern.compile("([a-z0-9_-]+)\\.breezy\\.hr", Pattern.CASE_INSENSITIVE), "breezy"},
            new Object[]{Pattern.compile("([a-z0-9-]+)\\.jobs\\.personio\\.(?:com|de)", Pattern.CASE_INSENSITIVE), "personio"},
            new Object[]{Pattern.compile("([a-z0-9-]+)\\.bamboohr\\.com", Pattern.CASE_INSENSITIVE), "bamboohr"}
    );
    // Workday needs tenant + data-centre + site, so it's detected separately.
    private static final Pattern WORKDAY_URL = Pattern.compile(
            "([a-z0-9-]+)\\.(wd\\d+)\\.myworkdayjobs\\.com/(?:[a-z]{2}-[A-Z]{2}/)?([a-zA-Z0-9_-]+)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> URL_STOP = Set.of("www", "apply", "jobs", "careers", "boards", "job-boards");

    /** Build a stable board URL from (type, token) that {@link #detectFromUrl} will match. */
    public static String canonicalBoardUrl(String type, String token) {
        if (type == null || token == null) return null;
        return switch (type) {
            case "greenhouse" -> "https://boards.greenhouse.io/" + token;
            case "lever" -> "https://jobs.lever.co/" + token;
            case "recruitee" -> "https://" + token + ".recruitee.com";
            case "smartrecruiters" -> "https://careers.smartrecruiters.com/" + token;
            case "workable" -> "https://apply.workable.com/" + token;
            case "ashby" -> "https://jobs.ashbyhq.com/" + token;
            case "breezy" -> "https://" + token + ".breezy.hr";
            case "personio" -> "https://" + token + ".jobs.personio.com";
            case "bamboohr" -> "https://" + token + ".bamboohr.com/careers";
            case "workday" -> {
                String[] p = token.split("~", 3);
                yield p.length < 3 ? null : "https://" + p[0] + "." + p[1] + ".myworkdayjobs.com/" + p[2];
            }
            default -> null;
        };
    }

    /** @return [type, token] if the URL points at a known ATS, else empty. */
    public Optional<String[]> detectFromUrl(String url) {
        if (url == null) return Optional.empty();
        Matcher wd = WORKDAY_URL.matcher(url);
        if (wd.find()) {
            return Optional.of(new String[]{"workday", wd.group(1) + "~" + wd.group(2) + "~" + wd.group(3)});
        }
        for (Object[] p : URL_PATTERNS) {
            Matcher m = ((Pattern) p[0]).matcher(url);
            if (m.find()) {
                String token = m.group(1);
                if (token != null && !URL_STOP.contains(token.toLowerCase())) {
                    return Optional.of(new String[]{(String) p[1], token});
                }
            }
        }
        return Optional.empty();
    }

    private static final Set<String> STOP = Set.of("the", "pty", "ltd", "group", "sa", "africa", "software",
            "technologies", "technology", "solutions", "systems", "digital", "holdings", "co", "inc");

    List<String> candidateTokens(Company company) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String name = company.name() == null ? "" : company.name().toLowerCase().replaceAll("[^a-z0-9 ]", " ").trim();
        List<String> words = Arrays.stream(name.split("\\s+")).filter(w -> !w.isBlank() && !STOP.contains(w)).toList();
        if (!words.isEmpty()) {
            out.add(String.join("", words));
            out.add(String.join("-", words));
            out.add(words.get(0));
        }
        String compact = name.replaceAll("\\s+", "");
        if (!compact.isBlank()) out.add(compact);
        String dom = domainRoot(company.apply());
        if (dom != null) { out.add(dom); out.add(dom + "careers"); }
        return out.stream().filter(t -> t != null && t.length() >= 2).limit(6).toList();
    }

    private static final Pattern DOMAIN = Pattern.compile("https?://(?:www\\.)?([a-z0-9-]+)\\.", Pattern.CASE_INSENSITIVE);

    private String domainRoot(String url) {
        if (url == null) return null;
        Matcher m = DOMAIN.matcher(url);
        return m.find() ? m.group(1).toLowerCase() : null;
    }

    // ---- providers ------------------------------------------------------

    public List<RawJob> fetch(String type, String token) {
        try {
            return switch (type) {
                case "greenhouse" -> greenhouse(token);
                case "lever" -> lever(token);
                case "recruitee" -> recruitee(token);
                case "smartrecruiters" -> smartrecruiters(token);
                case "workable" -> workable(token);
                case "ashby" -> ashby(token);
                case "breezy" -> breezy(token);
                case "personio" -> personio(token);
                case "workday" -> workday(token);
                case "bamboohr" -> bamboohr(token);
                default -> List.of();
            };
        } catch (Exception e) {
            return List.of();
        }
    }

    private JsonNode get(String url) {
        return http.get().uri(url).retrieve().body(JsonNode.class);
    }

    private JsonNode postJson(String url, String body) {
        return http.post().uri(url).header("Content-Type", "application/json")
                .body(body).retrieve().body(JsonNode.class);
    }

    // --- Personio ------------------------------------------------------------
    // {token}.jobs.personio.com/search.json  → array of positions
    private List<RawJob> personio(String token) {
        JsonNode d = get("https://" + token + ".jobs.personio.com/search.json?language=en");
        List<RawJob> out = new ArrayList<>();
        if (d != null && d.isArray()) for (JsonNode j : d) {
            String id = text(j, "id");
            out.add(new RawJob(text(j, "name"), text(j, "office"),
                    "https://" + token + ".jobs.personio.com/job/" + id, null,
                    joinNonEmpty(text(j, "seniority"), text(j, "department"))));
        }
        return out;
    }

    // --- BambooHR ------------------------------------------------------------
    // {token}.bamboohr.com/careers/list → {"result":[{jobOpeningName, id, ...}]}
    private List<RawJob> bamboohr(String token) {
        JsonNode d = get("https://" + token + ".bamboohr.com/careers/list");
        List<RawJob> out = new ArrayList<>();
        if (d != null) for (JsonNode j : d.path("result")) {
            String id = text(j, "id");
            String loc = firstNonEmpty(text(j, "locationLabel"),
                    joinNonEmpty(j.path("location").path("city").asText(""), j.path("location").path("state").asText("")));
            out.add(new RawJob(text(j, "jobOpeningName"), loc,
                    "https://" + token + ".bamboohr.com/careers/" + id, null, text(j, "departmentLabel")));
        }
        return out;
    }

    // --- Workday -------------------------------------------------------------
    // token = tenant~dc~site ; POST /wday/cxs/{tenant}/{site}/jobs → jobPostings[]
    private List<RawJob> workday(String token) {
        String[] p = token.split("~", 3);
        if (p.length < 3) return List.of();
        String tenant = p[0], dc = p[1], site = p[2];
        String base = "https://" + tenant + "." + dc + ".myworkdayjobs.com/" + site;
        String api = "https://" + tenant + "." + dc + ".myworkdayjobs.com/wday/cxs/" + tenant + "/" + site + "/jobs";
        JsonNode d = postJson(api, "{\"limit\":20,\"offset\":0,\"searchText\":\"\"}");
        List<RawJob> out = new ArrayList<>();
        if (d != null) for (JsonNode j : d.path("jobPostings")) {
            String path = text(j, "externalPath");
            String url = path.isBlank() ? base : base + path;
            out.add(new RawJob(text(j, "title"), text(j, "locationsText"), url, null, ""));
        }
        return out;
    }

    private List<RawJob> greenhouse(String token) {
        JsonNode d = get("https://boards-api.greenhouse.io/v1/boards/" + token + "/jobs?content=true");
        List<RawJob> out = new ArrayList<>();
        if (d != null) for (JsonNode j : d.path("jobs")) {
            out.add(new RawJob(text(j, "title"), j.path("location").path("name").asText(""),
                    text(j, "absolute_url"), parseDate(j.get("updated_at")), htmlToText(text(j, "content"))));
        }
        return out;
    }

    private List<RawJob> lever(String token) {
        for (String hostName : List.of("api.lever.co", "api.eu.lever.co")) {
            try {
                JsonNode d = get("https://" + hostName + "/v0/postings/" + token + "?mode=json");
                if (d != null && d.isArray()) {
                    List<RawJob> out = new ArrayList<>();
                    for (JsonNode p : d) {
                        out.add(new RawJob(text(p, "text"), p.path("categories").path("location").asText(""),
                                firstNonEmpty(text(p, "hostedUrl"), text(p, "applyUrl")), parseDate(p.get("createdAt")),
                                htmlToText(firstNonEmpty(text(p, "descriptionPlain"), text(p, "description")))));
                    }
                    return out;
                }
            } catch (Exception ignore) { /* try next host */ }
        }
        return List.of();
    }

    private List<RawJob> recruitee(String token) {
        JsonNode d = get("https://" + token + ".recruitee.com/api/offers/");
        List<RawJob> out = new ArrayList<>();
        if (d != null) for (JsonNode o : d.path("offers")) {
            String loc = firstNonEmpty(o.path("city").asText(""), o.path("location").asText(""));
            out.add(new RawJob(text(o, "title"), loc,
                    firstNonEmpty(text(o, "careers_url"), text(o, "careers_apply_url")), parseDate(o.get("published_at")),
                    htmlToText(text(o, "description"))));
        }
        return out;
    }

    private List<RawJob> smartrecruiters(String token) {
        JsonNode d = get("https://api.smartrecruiters.com/v1/companies/" + token + "/postings?limit=100");
        List<RawJob> out = new ArrayList<>();
        if (d != null) for (JsonNode p : d.path("content")) {
            String loc = joinNonEmpty(p.path("location").path("city").asText(""), p.path("location").path("country").asText(""));
            out.add(new RawJob(text(p, "name"), loc,
                    "https://jobs.smartrecruiters.com/" + token + "/" + p.path("id").asText(""), parseDate(p.get("releasedDate")),
                    ""));
        }
        return out;
    }

    private List<RawJob> workable(String token) {
        JsonNode d = get("https://apply.workable.com/api/v1/widget/accounts/" + token + "?details=true");
        List<RawJob> out = new ArrayList<>();
        if (d != null) for (JsonNode j : d.path("jobs")) {
            String loc = firstNonEmpty(j.path("location").path("location_str").asText(""),
                    joinNonEmpty(j.path("location").path("city").asText(""), j.path("location").path("country").asText("")));
            String url = firstNonEmpty(text(j, "url"), text(j, "shortlink"),
                    "https://apply.workable.com/" + token + "/j/" + j.path("shortcode").asText("") + "/");
            out.add(new RawJob(text(j, "title"), loc, url, parseDate(j.get("published_on")),
                    htmlToText(firstNonEmpty(text(j, "description"), text(j, "requirements")))));
        }
        return out;
    }

    private List<RawJob> ashby(String token) {
        JsonNode d = get("https://api.ashbyhq.com/posting-api/job-board/" + token + "?includeCompensation=false");
        List<RawJob> out = new ArrayList<>();
        if (d != null) for (JsonNode j : d.path("jobs")) {
            out.add(new RawJob(text(j, "title"), firstNonEmpty(text(j, "location"), text(j, "locationName")),
                    firstNonEmpty(text(j, "applyUrl"), text(j, "jobUrl")), parseDate(j.get("publishedAt")),
                    htmlToText(firstNonEmpty(text(j, "descriptionPlain"), text(j, "description")))));
        }
        return out;
    }

    private List<RawJob> breezy(String token) {
        JsonNode d = get("https://" + token + ".breezy.hr/json");
        List<RawJob> out = new ArrayList<>();
        if (d != null && d.isArray()) for (JsonNode p : d) {
            String loc = firstNonEmpty(p.path("location").path("name").asText(""),
                    joinNonEmpty(p.path("location").path("city").asText(""), p.path("location").path("country").path("name").asText("")));
            String url = firstNonEmpty(text(p, "url"),
                    "https://" + token + ".breezy.hr/p/" + p.path("_id").asText(""));
            out.add(new RawJob(firstNonEmpty(text(p, "name"), text(p, "title")), loc, url, parseDate(p.get("published_date")),
                    htmlToText(text(p, "description"))));
        }
        return out;
    }

    // ---- helpers --------------------------------------------------------

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText("");
    }

    private static String firstNonEmpty(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return "";
    }

    /** Unescape entities, strip tags, collapse whitespace, cap length. */
    private static String htmlToText(String html) {
        if (html == null || html.isBlank()) return "";
        String s = html
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&rsquo;", "'").replace("&nbsp;", " ");
        s = s.replaceAll("(?s)<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return s.length() > 5000 ? s.substring(0, 5000) : s;
    }

    private static String joinNonEmpty(String a, String b) {
        List<String> parts = new ArrayList<>();
        if (a != null && !a.isBlank()) parts.add(a);
        if (b != null && !b.isBlank()) parts.add(b);
        return String.join(", ", parts);
    }

    private static LocalDateTime parseDate(JsonNode node) {
        if (node == null || node.isNull()) return null;
        try {
            if (node.isNumber()) return LocalDateTime.ofInstant(Instant.ofEpochMilli(node.asLong()), ZoneId.systemDefault());
            String s = node.asText("");
            if (s.isBlank()) return null;
            try { return OffsetDateTime.parse(s).toLocalDateTime(); }
            catch (Exception e) { return LocalDateTime.parse(s.replace("Z", "")); }
        } catch (Exception e) {
            return null;
        }
    }
}
