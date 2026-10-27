package com.sanele.jobtracker.dto;

import com.sanele.jobtracker.entity.User;

public record ProfileResponse(
        String username,
        String email,
        String cv,
        String cvFileName,
        String desiredJob,
        Double yearsExperience,
        String jobCategory
) {
    public static ProfileResponse from(User u) {
        return new ProfileResponse(u.getUsername(), u.getEmail(), u.getCv(), u.getCvFileName(),
                u.getDesiredJob(), u.getYearsExperience(), u.getJobCategory());
    }
}
