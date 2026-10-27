package com.sanele.jobtracker.scan;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Estimates a broad occupation category from a user's desired-job text (falling
 * back to their CV). The buckets follow the SA Public Service Occupational
 * Dictionary's sections, flattened into the everyday groupings a job-seeker
 * recognises. First match wins, so ordering matters (IT is checked before Trades
 * so "software engineer" lands in IT, not the generic "engineer/technician" trade).
 */
@Component
public class JobCategoryEstimator {

    public static final String OTHER = "General / Other";

    private record Cat(String name, Pattern kw) {}

    private static Cat cat(String name, String regex) {
        return new Cat(name, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
    }

    private static final List<Cat> CATS = List.of(
            cat("IT & Engineering",
                "developer|software|programmer|\\bit\\b|information technology|data (scientist|analyst|engineer)|"
                + "devops|network|sysadmin|system admin|cyber|\\bqa\\b|tester|web dev|frontend|front[- ]?end|"
                + "backend|back[- ]?end|full[ -]?stack|cloud|database|\\bdba\\b|machine learning|\\bai\\b|"
                + "software engineer|electrical engineer|mechanical engineer|civil engineer|engineer(ing)?"),
            cat("Healthcare & Medicine",
                "nurse|nursing|doctor|physician|medical|clinic|pharmac|dental|dentist|surgeon|radiograph|"
                + "physio|therap|paramedic|caregiver|\\bhealth\\b|midwi|optomet|psycholog|dietit|"
                + "lab technolog|phlebotom|sonograph|occupational health"),
            cat("Business, Finance & Administration",
                "account|finance|financial|bookkeep|audit|\\btax\\b|\\bbank\\b|invest|actuar|admin|clerk|"
                + "secretar|receptionist|human resource|\\bhr\\b|payroll|procure|office manager|data capture|"
                + "business analyst|economist|insurance|underwrit|compliance"),
            cat("Trades & Construction",
                "electric|plumb|carpent|weld|fitter|boilermaker|artisan|builder|construction|mason|bricklay|"
                + "painter|plaster|millwright|rigger|scaffold|tiler|roofer|handyman|maintenance|mechanic\\b|"
                + "diesel|panel beat|technician"),
            cat("Education & Social Services",
                "teacher|teaching|lectur|tutor|educat|professor|instructor|principal|social work|counsel|"
                + "librarian|childcare|early childhood|\\btrainer\\b|facilitator"),
            cat("Science & Agriculture",
                "scientist|laborator|chemist|biolog|research|agricultur|\\bfarm|forestr|fisher|veterinar|"
                + "environmental|geolog|botan|microbiolog|horticultur"),
            cat("Sales, Retail & Hospitality",
                "sales|retail|cashier|merchandis|store manager|shop|hospitality|\\bchef\\b|cook|waiter|"
                + "waitress|barista|hotel|tourism|restaurant|customer service|call cent(re|er)|marketing|brand"),
            cat("Transport & Logistics",
                "driver|truck|logistic|warehouse|forklift|dispatch|courier|delivery|supply chain|fleet|"
                + "\\btransport|pilot|freight|stock controller"),
            cat("Legal, Media & Creative",
                "lawyer|attorney|\\blegal\\b|paralegal|advocate|journalist|\\bmedia\\b|graphic design|designer|"
                + "photograph|videograph|content|copywriter|\\bwriter\\b|editor|\\bartist\\b|architect|creative")
    );

    /** Best-guess category for a desired job, using the CV as a fallback signal. */
    public String estimate(String desiredJob, String cv) {
        String job = desiredJob == null ? "" : desiredJob;
        for (Cat c : CATS) if (c.kw().matcher(job).find()) return c.name();
        String cvText = cv == null ? "" : cv;
        for (Cat c : CATS) if (c.kw().matcher(cvText).find()) return c.name();
        return OTHER;
    }
}
