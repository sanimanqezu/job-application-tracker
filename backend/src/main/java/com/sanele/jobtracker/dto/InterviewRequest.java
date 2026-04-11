package com.sanele.jobtracker.dto;

import com.sanele.jobtracker.entity.InterviewType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record InterviewRequest(
        @NotNull LocalDateTime interviewDate,
        @NotNull InterviewType type,
        String notes,
        boolean completed
) {}
