package com.sanele.jobtracker.dto;

import com.sanele.jobtracker.entity.Interview;
import com.sanele.jobtracker.entity.InterviewType;

import java.time.LocalDateTime;

public record InterviewResponse(
        Long id,
        Long applicationId,
        LocalDateTime interviewDate,
        InterviewType type,
        String notes,
        boolean completed
) {
    public static InterviewResponse from(Interview interview) {
        return new InterviewResponse(
                interview.getId(),
                interview.getApplication().getId(),
                interview.getInterviewDate(),
                interview.getType(),
                interview.getNotes(),
                interview.isCompleted()
        );
    }
}
