package com.sanele.jobtracker.scan;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Scores a job against Sanele Khanyile's CV by reading the title + description.
 *
 * Profile (from the résumé): Full-Stack developer, ~1 year experience.
 * Core stack: Java / Spring Boot / Spring Cloud, Next.js / React / Node,
 * TypeScript, microservices; plus Postgres, Kafka, Docker, AWS, Keycloak, etc.
 * Seniority fit: junior / graduate / intermediate (1–2 yrs); senior/lead roles
 * are penalised because they don't fit the candidate.
 */
@Component
public class CvMatcher {

    public record MatchResult(int score, String matchedSkills) {}

    private record Skill(String display, Pattern pattern, int weight) {}

    // weight 5 = core identity of the CV, 4 = strong, 3 = supporting
    // SKILLS  = the default profile (Sanele's CV), used when no CV is supplied.
    // CATALOG = a much wider tech vocabulary, used to build a skill profile out of
    //           whatever CV a user pastes in (CATALOG includes every SKILLS entry).
    private static final List<Skill> SKILLS = new ArrayList<>();
    private static final List<Skill> CATALOG = new ArrayList<>();
    private static void skill(String display, int weight, String regex) {
        SKILLS.add(new Skill(display, Pattern.compile(regex, Pattern.CASE_INSENSITIVE), weight));
    }
    private static void cat(String display, int weight, String regex) {
        CATALOG.add(new Skill(display, Pattern.compile(regex, Pattern.CASE_INSENSITIVE), weight));
    }
    static {
        skill("Java", 5, "\\bjava\\b");
        skill("Spring Boot", 5, "spring ?boot");
        skill("Spring", 4, "\\bspring\\b");
        skill("Spring Cloud", 4, "spring ?cloud");
        skill("Microservices", 5, "micro ?service");
        skill("Full Stack", 5, "full[ -]?stack");
        skill("React", 5, "\\breact(\\.js|js)?\\b");
        skill("Next.js", 5, "next\\.?js");
        skill("Node.js", 4, "node(\\.js|js)?\\b");
        skill("TypeScript", 4, "type ?script|\\bts\\b");
        skill("JavaScript", 3, "java ?script|\\bjs\\b");
        skill("REST APIs", 4, "\\brest\\b|restful|rest api");
        skill("Hibernate/JPA", 4, "hibernate|\\bjpa\\b");
        skill("PostgreSQL", 4, "postgre|postgresql|\\bpostgres\\b");
        skill("SQL", 3, "\\bsql\\b");
        skill("Oracle", 3, "\\boracle\\b");
        skill("Kafka", 4, "\\bkafka\\b");
        skill("OpenFeign", 3, "feign");
        skill("Quartz", 3, "quartz");
        skill("Resilience4j", 3, "resilience4j");
        skill("Spring Security", 4, "spring ?security");
        skill("Keycloak", 3, "keycloak");
        skill("OAuth2/OIDC", 3, "oauth|oidc|openid");
        skill("JWT", 3, "\\bjwt\\b");
        skill("Docker", 3, "docker|container");
        skill("AWS", 3, "\\baws\\b|amazon web");
        skill("CI/CD", 3, "ci/cd|ci cd|continuous (integration|delivery)|jenkins|gitlab ci|github actions");
        skill("Maven", 2, "\\bmaven\\b");
        skill("Git", 2, "\\bgit\\b|github|gitlab");
        skill("JUnit/Mockito", 3, "junit|mockito|unit test");
        skill("SonarQube", 2, "sonar");
        skill("Angular", 3, "\\bangular\\b");
        skill("Python", 3, "\\bpython\\b");
        skill("Go", 3, "\\bgolang\\b|\\bgo\\b(?! to| live| ahead)");
        skill("Tailwind", 2, "tailwind");
        skill("Flyway", 2, "flyway");
        skill("Agile/Scrum", 2, "\\bagile\\b|\\bscrum\\b");

        // --- wider catalog: lets ANY pasted CV build a real skill profile ---
        CATALOG.addAll(SKILLS);                     // the default stack is part of the catalog
        // languages
        cat("C#", 5, "\\bc#\\b|c[- ]?sharp");
        cat(".NET", 5, "\\.net\\b|dotnet|asp\\.net");
        cat("PHP", 5, "\\bphp\\b");
        cat("Ruby", 5, "\\bruby\\b");
        cat("C++", 5, "\\bc\\+\\+\\b|cpp");
        cat("C", 3, "\\bc language\\b|\\bansi c\\b");
        cat("Rust", 5, "\\brust\\b");
        cat("Kotlin", 5, "\\bkotlin\\b");
        cat("Swift", 5, "\\bswift\\b");
        cat("Scala", 4, "\\bscala\\b");
        cat("Elixir", 4, "\\belixir\\b");
        cat("Dart", 4, "\\bdart\\b");
        cat("R", 3, "\\br language\\b|\\br programming\\b");
        cat("Perl", 3, "\\bperl\\b");
        cat("Objective-C", 3, "objective[- ]?c");
        // frontend frameworks
        cat("Vue", 5, "\\bvue(\\.js)?\\b");
        cat("Svelte", 4, "\\bsvelte\\b");
        cat("Redux", 3, "\\bredux\\b");
        cat("jQuery", 2, "jquery");
        cat("Bootstrap", 2, "bootstrap");
        cat("Sass/SCSS", 2, "\\bsass\\b|\\bscss\\b");
        // backend frameworks
        cat("Django", 5, "\\bdjango\\b");
        cat("Flask", 4, "\\bflask\\b");
        cat("FastAPI", 4, "fastapi");
        cat("Ruby on Rails", 5, "\\brails\\b|ruby on rails");
        cat("Laravel", 5, "\\blaravel\\b");
        cat("Symfony", 4, "symfony");
        cat("Express", 4, "express(\\.js)?");
        cat("NestJS", 4, "nest\\.?js");
        cat(".NET Core", 5, "\\.net core|asp\\.net core");
        cat("Quarkus", 3, "quarkus");
        // mobile
        cat("Flutter", 5, "\\bflutter\\b");
        cat("React Native", 5, "react[- ]?native");
        cat("Android", 4, "\\bandroid\\b");
        cat("iOS", 4, "\\bios\\b");
        cat("Xamarin", 3, "xamarin");
        // data / databases
        cat("MySQL", 4, "\\bmysql\\b|mariadb");
        cat("MongoDB", 4, "mongo\\b|mongodb");
        cat("Redis", 3, "\\bredis\\b");
        cat("Elasticsearch", 3, "elasticsearch|\\belastic\\b");
        cat("SQL Server", 3, "sql server|\\bt-?sql\\b|mssql");
        cat("GraphQL", 4, "graphql");
        cat("Cassandra", 3, "cassandra");
        cat("Snowflake", 3, "snowflake");
        // cloud / devops
        cat("Azure", 4, "\\bazure\\b");
        cat("GCP", 4, "\\bgcp\\b|google cloud");
        cat("Kubernetes", 4, "kubernetes|\\bk8s\\b");
        cat("Terraform", 3, "terraform");
        cat("Ansible", 3, "ansible");
        cat("Linux", 3, "\\blinux\\b|\\bunix\\b");
        cat("Bash", 2, "\\bbash\\b|shell script");
        cat("RabbitMQ", 3, "rabbitmq|\\bamqp\\b");
        cat("gRPC", 3, "grpc");
        cat("Nginx", 2, "nginx");
        cat("Serverless/Lambda", 3, "serverless|\\blambda\\b");
        // testing
        cat("Selenium", 3, "selenium");
        cat("Cypress", 3, "cypress");
        cat("Jest", 2, "\\bjest\\b");
        cat("Playwright", 2, "playwright");
        cat("Cucumber", 2, "cucumber");
        // data science / ML
        cat("Pandas", 3, "pandas");
        cat("NumPy", 3, "numpy");
        cat("TensorFlow", 4, "tensorflow");
        cat("PyTorch", 4, "pytorch");
        cat("scikit-learn", 3, "scikit|sklearn");
        cat("Spark", 3, "\\bspark\\b");
        cat("Hadoop", 3, "hadoop");
        cat("Power BI", 3, "power ?bi");
        cat("Tableau", 3, "tableau");
        cat("Airflow", 3, "airflow");
        // platforms
        cat("WordPress", 3, "wordpress");
        cat("Salesforce", 3, "salesforce|apex");
        cat("SAP", 3, "\\bsap\\b|abap");
        cat("Elasticsearch/ELK", 2, "\\belk\\b|logstash|kibana");
    }

    private static final Pattern JUNIOR = Pattern.compile(
            "\\b(junior|jnr|graduate|grad|intern|internship|entry[- ]?level|trainee|associate|early[- ]career|0[\\s-]?2\\s*years?|1[\\s-]?2\\s*years?|1\\s*year)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SENIOR = Pattern.compile(
            "\\b(senior|snr|sr\\.?|lead|principal|staff|head of|architect|manager|expert|specialist iii|iii\\b|[5-9]\\+?\\s*years|10\\+?\\s*years|1[0-9]\\s*years)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DEV_ROLE = Pattern.compile(
            "developer|engineer|software|full[ -]?stack|programmer", Pattern.CASE_INSENSITIVE);

    /**
     * Score against the default profile (Sanele's CV, ~1 year → junior fit).
     *
     * @param title       job title (weighted double)
     * @param description job description text (may be empty)
     */
    public MatchResult match(String title, String description) {
        return score(title, description, SKILLS, 1);
    }

    /** Core scorer: skills (≤60) + dev-role bonus + years-based seniority fit. */
    private MatchResult score(String title, String description, List<Skill> profile, double years) {
        String t = title == null ? "" : title.toLowerCase();
        String d = description == null ? "" : description.toLowerCase();

        int skillPoints = 0;
        // preserve weight order so the strongest matches show first
        Map<String, Integer> matched = new LinkedHashMap<>();
        for (Skill s : profile) {
            boolean inTitle = s.pattern().matcher(t).find();
            boolean inDesc = !inTitle && s.pattern().matcher(d).find();
            if (inTitle || inDesc) {
                skillPoints += inTitle ? s.weight() * 2 : s.weight();
                matched.put(s.display(), s.weight());
            }
        }

        int score = Math.min(60, skillPoints * 3);          // skills → up to 60
        if (DEV_ROLE.matcher(t).find()) score += 12;         // it's actually a dev role
        boolean jr = JUNIOR.matcher(t).find() || JUNIOR.matcher(d).find();
        boolean sr = SENIOR.matcher(t).find();               // seniority judged on title
        score += seniorityFit(jr, sr, years);
        score = Math.max(0, Math.min(100, score));

        // top matched skills by weight, then insertion order
        List<String> top = new ArrayList<>(matched.keySet());
        top.sort((a, b) -> matched.get(b) - matched.get(a));
        String skills = String.join(", ", top.subList(0, Math.min(10, top.size())));

        return new MatchResult(score, skills);
    }

    /**
     * How well a role's level fits the candidate's experience.
     * ≤2 yrs: reward junior, penalise senior (the original behaviour).
     * 3–5 yrs: mild penalty either way, slight bonus for un-flagged (mid) roles.
     * 6+ yrs: reward senior, penalise junior (over-qualified).
     */
    private static int seniorityFit(boolean jr, boolean sr, double years) {
        if (years <= 2) return (jr ? 25 : 0) + (sr ? -35 : 0);
        if (years <= 5) return (sr ? -12 : 0) + (jr ? -6 : 8);
        return (sr ? 25 : 0) + (jr ? -25 : 0);
    }

    // --- generic, profession-agnostic matching (any field, not just tech) ---

    // Generic English + CV/advert boilerplate that carries no field signal.
    private static final Set<String> STOP = Set.of(
            "the", "and", "for", "with", "you", "your", "our", "are", "was", "were", "has", "have", "had",
            "this", "that", "these", "those", "from", "will", "would", "shall", "not", "but", "all", "any",
            "can", "could", "job", "jobs", "role", "roles", "work", "works", "working", "year", "years",
            "experience", "experienced", "team", "teams", "skill", "skills", "ability", "abilities",
            "strong", "good", "excellent", "including", "include", "includes", "responsible",
            "responsibilities", "requirement", "requirements", "required", "require", "must", "should",
            "knowledge", "using", "use", "used", "within", "across", "new", "per", "via", "etc", "who",
            "how", "what", "when", "where", "which", "candidate", "candidates", "applicant", "applicants",
            "looking", "join", "company", "companies", "business", "based", "full", "time", "part",
            "position", "positions", "opportunity", "opportunities", "environment", "related", "field",
            "degree", "qualification", "qualifications", "relevant", "proven", "plus", "well", "also",
            "other", "their", "them", "its", "into", "out", "more", "most", "than", "then", "month",
            "months", "week", "weeks", "daily", "help", "like", "need", "needs", "able", "may", "one",
            "two", "three", "about", "being", "been", "such", "each", "both", "between", "during",
            "before", "after", "here", "there", "while", "because", "through", "own", "get", "make",
            "ensure", "provide", "provided");

    // Short tokens (<3 chars) worth keeping despite the length floor.
    private static final Set<String> KEEP_SHORT = Set.of("qa", "hr", "ux", "ui", "js", "pa");

    /** Meaningful, de-duplicated lowercase word tokens from a blob of text. */
    private static List<String> tokens(String text) {
        if (text == null || text.isBlank()) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String w : text.toLowerCase().split("[^a-z0-9+#.]+")) {
            w = w.replaceAll("^\\.+|\\.+$", "");            // trim stray dots
            if (w.isBlank()) continue;
            if (w.length() < 3 && !KEEP_SHORT.contains(w)) continue;
            if (STOP.contains(w)) continue;
            out.add(w);
        }
        return new ArrayList<>(out);
    }

    private static String cap(String w) {
        return w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1);
    }

    private static String nz(String s) { return s == null ? "" : s; }

    /**
     * Profession-agnostic match: rank a job against a user's own CV + desired job +
     * years of experience. Works for ANY field (nurse, electrician, accountant, dev)
     * by keyword overlap — the desired-job words are the dominant signal, the CV adds
     * breadth, recognised named skills add precision, and years drive the level fit.
     */
    public MatchResult matchProfile(String title, String description, String cvText,
                                    String desiredJob, double years) {
        List<String> core = tokens(desiredJob);
        LinkedHashSet<String> cvWords = new LinkedHashSet<>(tokens(cvText));
        core.forEach(cvWords::remove);                      // never count a word twice
        if (core.isEmpty() && cvWords.isEmpty())
            return score(title, description, SKILLS, years); // no usable profile → default

        Set<String> titleTok = new HashSet<>(tokens(title));
        Set<String> descTok = new HashSet<>(tokens(description));
        LinkedHashMap<String, String> shown = new LinkedHashMap<>();   // lowerKey -> display

        // 1) desired-job alignment — the dominant signal
        int coreHit = 0;
        boolean coreInTitle = false;
        for (String w : core) {
            boolean inT = titleTok.contains(w), inD = descTok.contains(w);
            if (inT || inD) { coreHit++; if (inT) coreInTitle = true; shown.putIfAbsent(w, cap(w)); }
        }
        // 2) recognised named skills present in BOTH the CV and the job (precise, multi-word aware)
        String cvLower = nz(cvText).toLowerCase();
        String jobText = (nz(title) + " " + nz(description)).toLowerCase();
        int skillPts = 0;
        for (Skill s : CATALOG) {
            if (s.pattern().matcher(cvLower).find() && s.pattern().matcher(jobText).find()) {
                skillPts += s.weight();
                shown.putIfAbsent(s.display().toLowerCase(), s.display());
            }
        }
        // 3) CV keyword breadth
        int cvHit = 0;
        for (String w : cvWords) {
            if (titleTok.contains(w) || descTok.contains(w)) { cvHit++; shown.putIfAbsent(w, cap(w)); }
        }

        int sc = 0;
        if (!core.isEmpty()) sc += Math.round(40f * coreHit / core.size());
        if (coreInTitle) sc += 12;
        sc += Math.min(20, skillPts * 2);
        sc += Math.min(20, cvHit * 3);

        boolean jr = JUNIOR.matcher(nz(title)).find() || JUNIOR.matcher(nz(description)).find();
        boolean sr = SENIOR.matcher(nz(title)).find();
        sc += seniorityFit(jr, sr, years);
        sc = Math.max(0, Math.min(100, sc));

        List<String> top = new ArrayList<>(shown.values());
        String skills = String.join(", ", top.subList(0, Math.min(10, top.size())));
        return new MatchResult(sc, skills);
    }
}
