package com.sanele.jobtracker.dto;

import com.sanele.jobtracker.entity.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record JobApplicationRequest(
        @NotBlank String company,
        @NotBlank String role,
        String location,
        String jobUrl,
        @NotNull ApplicationStatus status,
        @NotNull LocalDate appliedDate,
        String salaryRange,
        String notes
) {}
