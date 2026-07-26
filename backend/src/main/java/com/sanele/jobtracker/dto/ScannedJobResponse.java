package com.sanele.jobtracker.dto;

import com.sanele.jobtracker.entity.ScannedJob;

import java.time.LocalDateTime;

public record ScannedJobResponse(
        Long id,
        String source,
        String company,
        String title,
        String location,
        String url,
        String stack,
        String segment,
        String city,
        boolean junior,
        int matchScore,
        String matchedSkills,
        String snippet,
        LocalDateTime postedAt,
        LocalDateTime firstSeenAt
) {
    public static ScannedJobResponse from(ScannedJob j) {
        String desc = j.getDescription();
        String snippet = desc == null ? null : (desc.length() > 300 ? desc.substring(0, 300) + "…" : desc);
        return new ScannedJobResponse(j.getId(), j.getSource(), j.getCompany(), j.getTitle(),
                j.getLocation(), j.getUrl(), j.getStack(), j.getSegment(), j.getCity(),
                j.isJunior(), j.getMatchScore(), j.getMatchedSkills(), snippet,
                j.getPostedAt(), j.getFirstSeenAt());
    }
}
