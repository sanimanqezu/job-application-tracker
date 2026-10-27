package com.sanele.jobtracker.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank @Size(min = 10, max = 20000) String cv,
        @Size(max = 255) String cvFileName,
        @NotBlank @Size(max = 150) String desiredJob,
        @NotNull @DecimalMin("0") @DecimalMax("60") Double yearsExperience
) {}
