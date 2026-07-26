package com.sanele.jobtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A job opening discovered live from a company's applicant-tracking system.
 * Stored globally and de-duplicated on {@code url}.
 */
@Entity
@Table(name = "scanned_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScannedJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String source;

    @Column(nullable = false, length = 150)
    private String company;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 200)
    private String location;

    @Column(nullable = false, length = 600, unique = true)
    private String url;

    @Column(length = 200)
    private String stack;

    @Column(length = 80)
    private String segment;

    @Column(length = 120)
    private String city;

    @Column(nullable = false)
    private boolean junior;

    /** 0–100 relevance to the candidate's CV (title + description). */
    @Column(name = "match_score", nullable = false)
    private int matchScore;

    /** Comma-separated CV skills found in the posting. */
    @Column(name = "matched_skills", length = 500)
    private String matchedSkills;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @CreationTimestamp
    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private LocalDateTime firstSeenAt;

    @UpdateTimestamp
    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;
}
