package com.sanele.jobtracker.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One row of companies.json — a company or recruiter with a direct careers URL. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Company(
        String name,
        String city,
        String segment,
        String stack,
        String size,
        String fit,
        String apply,
        String note
) {}
