package com.sanele.jobtracker.scan;

import com.sanele.jobtracker.ats.AtsClient;
import com.sanele.jobtracker.ats.RawJob;
import com.sanele.jobtracker.catalog.Company;
import com.sanele.jobtracker.entity.CompanyEntity;
import com.sanele.jobtracker.entity.ScannedJob;
import com.sanele.jobtracker.repository.CompanyRepository;
import com.sanele.jobtracker.repository.ScannedJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runs the live scan: fetch every company's ATS boards, filter, and upsert (dedup on URL). */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobScanService {

    private final AtsClient ats;
    private final ScannedJobRepository repo;
    private final CompanyRepository companyRepo;
    private final CvMatcher cvMatcher;

    // South-African location filtering lives in SaLocation (reads the job's own
    // location, not the company name, and knows SA cities/provinces/townships).
    private static final Pattern DEV = Pattern.compile(
            "developer|engineer(ing)?|software|full[\\s-]?stack|back[\\s-]?end|front[\\s-]?end|web dev|\\bjava\\b|\\.net|\\bc#\\b|react|node|python|golang|\\bgo\\b|typescript|\\bqa\\b|test automation|programmer|devops|\\bsre\\b|mobile|android|\\bios\\b|data engineer|data scien|machine learning|\\bml\\b|cloud engineer",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern JUNIOR = Pattern.compile(
            "\\b(junior|jnr|graduate|grad|intern|internship|entry[- ]?level|trainee|associate|0[\\s-]?2\\s*years?)\\b",
            Pattern.CASE_INSENSITIVE);

    // --- hard exclusion rules (never store these) ---
    // Titles above the junior / ≤2-year band. Aggregators (Adzuna) truncate the
    // job body, so the TITLE is often the only reliable seniority signal — recruiters
    // almost always label the level there (Intermediate, Mid, Senior, II/III…).
    private static final Pattern SENIOR_TITLE = Pattern.compile(
            "\\b(senior|snr|sr\\.?|lead|principal|staff|head\\s+of|architect|manager|expert|director|vp|chief|"
            + "advanced|intermediate|mid[\\s-]?level|mid[\\s-]?senior|mid|experienced)\\b|\\b(ii|iii|iv)\\b",
            Pattern.CASE_INSENSITIVE);
    // "N years ... experience" / "experience ... N years" / "minimum/at least N years"
    private static final List<Pattern> YEARS_CTX = List.of(
            Pattern.compile("(\\d{1,2})\\s*\\+?\\s*years?[^.\\n]{0,30}experience", Pattern.CASE_INSENSITIVE),
            Pattern.compile("experience[^.\\n]{0,30}(\\d{1,2})\\s*\\+?\\s*years?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:minimum|at least|min\\.?)\\s*(?:of\\s*)?(\\d{1,2})\\s*\\+?\\s*years?", Pattern.CASE_INSENSITIVE));
    private static final int MIN_MATCH = 15;   // strictly greater than 15%

    /** Smallest required-experience figure found in the description, or -1 if none. */
    private static int minRequiredYears(String desc) {
        if (desc == null || desc.isBlank()) return -1;
        int min = Integer.MAX_VALUE;
        for (Pattern p : YEARS_CTX) {
            Matcher mm = p.matcher(desc);
            while (mm.find()) {
                try {
                    int y = Integer.parseInt(mm.group(1));
                    if (y >= 0 && y < 40) min = Math.min(min, y);
                } catch (NumberFormatException ignore) { /* skip */ }
            }
        }
        return min == Integer.MAX_VALUE ? -1 : min;
    }

    /** A raw job tagged with its source company (used between the fetch and upsert phases). */
    private record Found(Company company, String source, RawJob job) {}

    public record ScanSummary(int companiesScanned, int boardsWithOpenings, int jobsDiscovered,
                              int jobsStored, int newlyAdded) {}

    /** Scan every company in the DB, using each one's learned ATS URL when available. */
    public ScanSummary scan(boolean probe) {
        List<Company> companies = companyRepo.findAll().stream().map(JobScanService::toDto).toList();
        return scanBatch(companies, probe);
    }

    /** Map a stored company to the scan DTO, preferring its learned ATS URL. */
    static Company toDto(CompanyEntity c) {
        String url = (c.getAtsUrl() != null && !c.getAtsUrl().isBlank()) ? c.getAtsUrl() : c.getApplyUrl();
        return new Company(c.getName(), c.getCity(), c.getSegment(), c.getStack(), null, c.getFit(), url, c.getNote());
    }

    /**
     * Scan a specific set of companies (used by the scheduled harvester and the
     * manual full scan).
     *
     * @param probe if true, also guess ATS tokens (thorough, slower); otherwise only
     *              use boards detectable from each careers URL (fast).
     */
    public ScanSummary scanBatch(List<Company> companies, boolean probe) {
        long start = System.currentTimeMillis();

        // --- phase 1: fetch boards in parallel (network-bound) ---
        // High concurrency: a fast full scan of ~1000 companies finishes in a few
        // seconds (only companies with a known ATS URL make a network call).
        ExecutorService pool = Executors.newFixedThreadPool(probe ? 64 : 120);
        List<Found> found = new ArrayList<>();
        Map<String, String> learnedAts = new ConcurrentHashMap<>();
        int boards = 0;
        try {
            List<Future<List<AtsClient.Board>>> futures = new ArrayList<>();
            for (Company c : companies) futures.add(pool.submit(() -> ats.discover(c, probe)));
            for (int i = 0; i < companies.size(); i++) {
                try {
                    List<AtsClient.Board> bs = futures.get(i).get(45, TimeUnit.SECONDS);
                    for (AtsClient.Board b : bs) {
                        boards++;
                        String canon = AtsClient.canonicalBoardUrl(b.type(), b.token());
                        if (canon != null) learnedAts.putIfAbsent(companies.get(i).name(), canon);
                        for (RawJob j : b.jobs()) found.add(new Found(companies.get(i), b.type(), j));
                    }
                } catch (Exception e) {
                    log.debug("scan failed for {}: {}", companies.get(i).name(), e.toString());
                }
            }
        } finally {
            pool.shutdownNow();
        }

        // cache each company's discovered ATS so future scans use the fast path
        rememberAts(learnedAts);

        // --- phase 2: filter + upsert (dedup on URL) ---
        int newly = upsert(found);
        long total = repo.count();
        log.info("Scan[{}] done in {}ms: {} boards, {} discovered, {} new, {} total",
                probe ? "deep" : "fast", System.currentTimeMillis() - start, boards, found.size(), newly, total);
        return new ScanSummary(companies.size(), boards, found.size(), (int) total, newly);
    }

    // Each repo call runs in its own transaction — deliberate, so the long
    // network phase above is never inside an open DB transaction.
    private int upsert(List<Found> found) {
        int newly = 0;
        for (Found f : found) {
            RawJob j = f.job();
            if (j.url() == null || j.url().isBlank() || j.title() == null || j.title().isBlank()) continue;
            String url = trim(j.url(), 600);

            // Must be a South-African location (read the job's OWN location, not the
            // company name) and a dev role. If a row like this was wrongly stored
            // by the old filter, delete it so the board self-heals on re-scan.
            if (!SaLocation.isSouthAfrican(j.location()) || !DEV.matcher(j.title()).find()) {
                repo.findByUrl(url).ifPresent(repo::delete);
                continue;
            }

            CvMatcher.MatchResult m = cvMatcher.match(j.title(), j.description());

            // Hard exclusions: senior/mid titles, >2 years required, or match <= 15%.
            // Scan the TITLE as well as the description for a years requirement, since
            // aggregator bodies are truncated (e.g. "Full Stack Developer (3-5 years)").
            String yearsText = j.title() + ". " + (j.description() == null ? "" : j.description());
            boolean excluded = SENIOR_TITLE.matcher(j.title()).find()
                    || m.score() <= MIN_MATCH
                    || minRequiredYears(yearsText) > 2;

            var existing = repo.findByUrl(url);
            if (existing.isPresent()) {
                ScannedJob e = existing.get();
                if (excluded) { repo.delete(e); continue; }   // no longer qualifies → remove it
                e.setLastSeenAt(java.time.LocalDateTime.now());
                // backfill match/description for rows stored before CV matching existed
                if (e.getMatchScore() == 0) {
                    e.setMatchScore(m.score());
                    e.setMatchedSkills(trim(m.matchedSkills(), 500));
                    if (j.description() != null && !j.description().isBlank()) e.setDescription(trim(j.description(), 5000));
                }
                repo.save(e);
                continue;
            }
            if (excluded) continue;   // never store senior / over-experienced / low-match roles
            repo.save(ScannedJob.builder()
                    .source(f.source())
                    .company(f.company().name())
                    .title(trim(j.title(), 300))
                    .location(trim(j.location(), 200))
                    .url(url)
                    .stack(trim(f.company().stack(), 200))
                    .segment(trim(f.company().segment(), 80))
                    .city(trim(f.company().city(), 120))
                    .junior(JUNIOR.matcher(j.title()).find())
                    .matchScore(m.score())
                    .matchedSkills(trim(m.matchedSkills(), 500))
                    .description(trim(j.description(), 5000))
                    .postedAt(j.postedAt())
                    .build());
            newly++;
        }
        return newly;
    }

    /** A job from an external source (e.g. Adzuna) with its own company name. */
    public record SourcedJob(String company, String segment, String city, RawJob job) {}

    /**
     * Store jobs from an external source (Adzuna, etc.) through the SAME pipeline as
     * ATS scans — South-African location filter, dev-role check, seniority / &gt;2-year
     * exclusion, CV match and URL dedup. Returns how many were newly added.
     */
    public int storeExternalJobs(List<SourcedJob> jobs, String source) {
        List<Found> found = new ArrayList<>();
        for (SourcedJob sj : jobs) {
            Company c = new Company(sj.company(), sj.city(), sj.segment(), null, null, null, null, null);
            found.add(new Found(c, source, sj.job()));
        }
        int newly = upsert(found);
        log.info("External[{}]: {} jobs in, {} newly stored ({} total)", source, jobs.size(), newly, repo.count());
        return newly;
    }

    /** Remove any already-stored jobs whose location isn't South African (backlog cleanup). */
    @Transactional
    public int cleanupNonSouthAfrican() {
        List<ScannedJob> all = repo.findAll();
        int removed = 0;
        for (ScannedJob j : all) {
            if (!SaLocation.isSouthAfrican(j.getLocation())) {
                repo.delete(j);
                removed++;
            }
        }
        log.info("Location cleanup: removed {} non-South-African jobs ({} remain)", removed, repo.count());
        return removed;
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** Persist newly-discovered ATS URLs onto their companies (only when changed). */
    private void rememberAts(Map<String, String> learned) {
        learned.forEach((name, url) -> companyRepo.findByName(name).ifPresent(e -> {
            if (!Objects.equals(e.getAtsUrl(), url)) {
                e.setAtsUrl(url);
                companyRepo.save(e);
            }
        }));
    }
}
