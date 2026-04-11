package com.sanele.jobtracker.dto;

import com.sanele.jobtracker.entity.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(
        @NotNull ApplicationStatus status
) {}
