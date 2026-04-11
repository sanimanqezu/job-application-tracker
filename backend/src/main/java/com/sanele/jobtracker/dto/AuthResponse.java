package com.sanele.jobtracker.dto;

public record AuthResponse(
        String token,
        String username,
        String email
) {}
