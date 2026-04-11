package com.sanele.jobtracker.service;

import com.sanele.jobtracker.dto.InterviewRequest;
import com.sanele.jobtracker.dto.InterviewResponse;
import com.sanele.jobtracker.entity.Interview;
import com.sanele.jobtracker.entity.JobApplication;
import com.sanele.jobtracker.entity.User;
import com.sanele.jobtracker.exception.ResourceNotFoundException;
import com.sanele.jobtracker.repository.InterviewRepository;
import com.sanele.jobtracker.repository.JobApplicationRepository;
import com.sanele.jobtracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final JobApplicationRepository applicationRepository;
    private final UserRepository userRepository;

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    @Transactional
    public InterviewResponse addInterview(String username, Long applicationId, InterviewRequest request) {
        User user = getUser(username);
        JobApplication application = applicationRepository.findByIdAndUserId(applicationId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));

        Interview interview = Interview.builder()
                .application(application)
                .interviewDate(request.interviewDate())
                .type(request.type())
                .notes(request.notes())
                .completed(request.completed())
                .build();

        return InterviewResponse.from(interviewRepository.save(interview));
    }

    @Transactional(readOnly = true)
    public List<InterviewResponse> getInterviews(String username, Long applicationId) {
        User user = getUser(username);
        applicationRepository.findByIdAndUserId(applicationId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));

        return interviewRepository.findByApplicationIdOrderByInterviewDateAsc(applicationId)
                .stream()
                .map(InterviewResponse::from)
                .toList();
    }

    @Transactional
    public void deleteInterview(String username, Long interviewId) {
        User user = getUser(username);
        Interview interview = interviewRepository.findByIdAndUserId(interviewId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found with id: " + interviewId));
        interviewRepository.delete(interview);
    }
}
