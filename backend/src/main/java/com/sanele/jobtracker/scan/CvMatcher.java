package com.sanele.jobtracker.scan;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final List<Skill> SKILLS = new ArrayList<>();
    private static void skill(String display, int weight, String regex) {
        SKILLS.add(new Skill(display, Pattern.compile(regex, Pattern.CASE_INSENSITIVE), weight));
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
     * @param title       job title (weighted double)
     * @param description job description text (may be empty)
     */
    public MatchResult match(String title, String description) {
        String t = title == null ? "" : title.toLowerCase();
        String d = description == null ? "" : description.toLowerCase();

        int skillPoints = 0;
        // preserve weight order so the strongest matches show first
        Map<String, Integer> matched = new LinkedHashMap<>();
        for (Skill s : SKILLS) {
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
        if (jr) score += 25;                                 // fits a junior candidate
        if (sr) score -= 35;                                 // over-senior for this CV
        score = Math.max(0, Math.min(100, score));

        // top matched skills by weight, then insertion order
        List<String> top = new ArrayList<>(matched.keySet());
        top.sort((a, b) -> matched.get(b) - matched.get(a));
        String skills = String.join(", ", top.subList(0, Math.min(10, top.size())));

        return new MatchResult(score, skills);
    }
}
