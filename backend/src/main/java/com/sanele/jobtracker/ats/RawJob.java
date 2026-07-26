package com.sanele.jobtracker.ats;

import java.time.LocalDateTime;

/** A single opening as returned by an ATS board, before normalization/filtering. */
public record RawJob(String title, String location, String url, LocalDateTime postedAt, String description) {}
