package com.sanele.jobtracker.dto;

import java.util.Map;

public record DashboardResponse(
        long totalApplications,
        Map<String, Long> byStatus,
        long thisMonthCount,
        long activeCount
) {}
