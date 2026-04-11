package com.sanele.jobtracker.dto;

import com.sanele.jobtracker.entity.ApplicationStatus;
import com.sanele.jobtracker.entity.JobApplication;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record JobApplicationResponse(
        Long id,
        String company,
        String role,
        String location,
        String jobUrl,
        ApplicationStatus status,
        LocalDate appliedDate,
        String salaryRange,
        String notes,
        LocalDateTime updatedAt,
        int interviewCount
) {
    public static JobApplicationResponse from(JobApplication app) {
        return new JobApplicationResponse(
                app.getId(),
                app.getCompany(),
                app.getRole(),
                app.getLocation(),
                app.getJobUrl(),
                app.getStatus(),
                app.getAppliedDate(),
                app.getSalaryRange(),
                app.getNotes(),
                app.getUpdatedAt(),
                app.getInterviews().size()
        );
    }
}
