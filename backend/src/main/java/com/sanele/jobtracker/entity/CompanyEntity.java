package com.sanele.jobtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** A company in the persistent universe that the harvester scans for jobs. */
@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200, unique = true)
    private String name;

    @Column(name = "apply_url", length = 600)
    private String applyUrl;

    /** ATS board URL learned by the deep probe — enables the fast scan path. */
    @Column(name = "ats_url", length = 600)
    private String atsUrl;

    @Column(length = 80)
    private String segment;

    @Column(length = 200)
    private String stack;

    @Column(length = 120)
    private String city;

    @Column(length = 20)
    private String fit;

    @Column(length = 500)
    private String note;

    /** Null until first scanned; the harvester picks the oldest first. */
    @Column(name = "last_scanned_at")
    private LocalDateTime lastScannedAt;

    @Column(name = "jobs_found", nullable = false)
    private int jobsFound;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
